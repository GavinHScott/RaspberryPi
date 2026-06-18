#!/usr/bin/env python3
"""Long-running NumPy N-body stability probe for the Sol/Kcalbeloh state.

This is intentionally independent of the KSP install.  It reads
initial_state.json from this directory and writes resumable checkpoints plus
CSV diagnostics into an output directory.
"""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
import shutil
import time

import numpy as np


DAY_SECONDS = 86_400.0
JULIAN_YEAR_DAYS = 365.25
ROOT = Path(__file__).resolve().parent


def load_state(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if data.get("schema_version") != 1:
        raise SystemExit(f"Unsupported state schema in {path}")
    return data


def arrays_from_data(data: dict) -> tuple[list[str], np.ndarray, np.ndarray, np.ndarray, np.ndarray, list[str]]:
    bodies = data["bodies"]
    names = [body["name"] for body in bodies]
    mu = np.array([body["mu_km3_s2"] for body in bodies], dtype=np.float64)
    radius = np.array(
        [np.nan if body.get("radius_km") is None else body["radius_km"] for body in bodies],
        dtype=np.float64,
    )
    positions = np.array([body["r_km"] for body in bodies], dtype=np.float64)
    velocities = np.array([body["v_km_s"] for body in bodies], dtype=np.float64)
    systems = [body.get("system", "unknown") for body in bodies]
    return names, mu, radius, positions, velocities, systems


def recenter_total_barycentre(positions: np.ndarray, velocities: np.ndarray, mu: np.ndarray) -> None:
    total = mu.sum()
    positions -= (positions * mu[:, None]).sum(axis=0) / total
    velocities -= (velocities * mu[:, None]).sum(axis=0) / total


def acceleration(positions: np.ndarray, mu: np.ndarray) -> np.ndarray:
    delta = positions[None, :, :] - positions[:, None, :]
    r2 = np.einsum("ijk,ijk->ij", delta, delta)
    np.fill_diagonal(r2, np.inf)
    inv_r3 = r2 ** -1.5
    weighted = inv_r3 * mu[None, :]
    return (delta * weighted[:, :, None]).sum(axis=1)


def total_energy(positions: np.ndarray, velocities: np.ndarray, mu: np.ndarray, pair_i: np.ndarray, pair_j: np.ndarray) -> tuple[float, float, float]:
    kinetic = 0.5 * np.sum(mu * np.einsum("ij,ij->i", velocities, velocities))
    delta = positions[pair_j] - positions[pair_i]
    distances = np.linalg.norm(delta, axis=1)
    potential = -np.sum(mu[pair_i] * mu[pair_j] / distances)
    return float(kinetic + potential), float(kinetic), float(potential)


def group_barycentre(positions: np.ndarray, mu: np.ndarray, indices: np.ndarray) -> np.ndarray:
    weights = mu[indices]
    return (positions[indices] * weights[:, None]).sum(axis=0) / weights.sum()


def group_barycentre_distance(
    positions: np.ndarray,
    mu: np.ndarray,
    systems: list[str],
    system_a: str = "Sol",
    system_b: str = "Kcalbeloh",
) -> float:
    a = np.array([i for i, system in enumerate(systems) if system == system_a], dtype=np.int64)
    b = np.array([i for i, system in enumerate(systems) if system == system_b], dtype=np.int64)
    if len(a) == 0 or len(b) == 0:
        return float("nan")
    return float(np.linalg.norm(group_barycentre(positions, mu, b) - group_barycentre(positions, mu, a)))


def monitor_values(monitors: list[dict], name_to_index: dict[str, int], positions: np.ndarray, mu: np.ndarray) -> np.ndarray:
    values: list[float] = []
    for monitor in monitors:
        if monitor["type"] == "pair_distance":
            i = name_to_index[monitor["a"]]
            j = name_to_index[monitor["b"]]
            values.append(float(np.linalg.norm(positions[i] - positions[j])))
        elif monitor["type"] == "barycentric_distance":
            body = name_to_index[monitor["body"]]
            centres = np.array([name_to_index[name] for name in monitor["centres"]], dtype=np.int64)
            centre = group_barycentre(positions, mu, centres)
            values.append(float(np.linalg.norm(positions[body] - centre)))
        else:
            raise SystemExit(f"Unknown monitor type: {monitor['type']}")
    return np.array(values, dtype=np.float64)


def pair_distances(positions: np.ndarray, pair_i: np.ndarray, pair_j: np.ndarray) -> np.ndarray:
    return np.linalg.norm(positions[pair_j] - positions[pair_i], axis=1)


def ensure_metadata(out_dir: Path, data_path: Path, data: dict, args: argparse.Namespace, names: list[str]) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    metadata = {
        "data_file": str(data_path.resolve()),
        "body_count": len(names),
        "bodies": names,
        "source": data.get("source", {}),
        "units": data.get("units", {}),
        "args": {
            "years": args.years,
            "dt_days": args.dt_days,
            "report_every_years": args.report_every_years,
            "checkpoint_every_years": args.checkpoint_every_years,
            "track_every_steps": args.track_every_steps,
            "recenter": not args.no_recenter,
        },
    }
    (out_dir / "run_metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    shutil.copy2(data_path, out_dir / "initial_state_used.json")


def open_progress(path: Path, resume: bool) -> tuple[object, csv.writer]:
    exists = path.exists() and resume
    handle = path.open("a" if exists else "w", newline="", encoding="utf-8")
    writer = csv.writer(handle)
    if not exists:
        writer.writerow(
            [
                "step",
                "time_years",
                "energy",
                "kinetic",
                "potential",
                "relative_energy_drift",
                "system_barycentre_distance_km",
                "closest_pair_so_far",
                "closest_pair_distance_km",
                "closest_known_clearance_km",
                "elapsed_seconds",
            ]
        )
    return handle, writer


def open_monitor_log(path: Path, monitors: list[dict], resume: bool) -> tuple[object, csv.writer]:
    exists = path.exists() and resume
    handle = path.open("a" if exists else "w", newline="", encoding="utf-8")
    writer = csv.writer(handle)
    if not exists:
        writer.writerow(["step", "time_years", "monitor", "distance_km"])
    return handle, writer


def write_checkpoint(
    path: Path,
    step: int,
    positions: np.ndarray,
    velocities: np.ndarray,
    min_pair_distance: np.ndarray,
    min_pair_step: np.ndarray,
    monitor_min: np.ndarray,
    monitor_max: np.ndarray,
    monitor_min_step: np.ndarray,
    monitor_max_step: np.ndarray,
    initial_energy: float,
) -> None:
    tmp = path.with_suffix(path.suffix + ".tmp")
    with tmp.open("wb") as handle:
        np.savez(
            handle,
            step=np.array(step, dtype=np.int64),
            positions=positions,
            velocities=velocities,
            min_pair_distance=min_pair_distance,
            min_pair_step=min_pair_step,
            monitor_min=monitor_min,
            monitor_max=monitor_max,
            monitor_min_step=monitor_min_step,
            monitor_max_step=monitor_max_step,
            initial_energy=np.array(initial_energy, dtype=np.float64),
        )
    tmp.replace(path)


def load_checkpoint(path: Path) -> dict:
    with np.load(path, allow_pickle=False) as data:
        return {key: data[key].copy() for key in data.files}


def write_final_state(path: Path, names: list[str], systems: list[str], mu: np.ndarray, radius: np.ndarray, positions: np.ndarray, velocities: np.ndarray) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["name", "system", "mu_km3_s2", "radius_km", "x_km", "y_km", "z_km", "vx_km_s", "vy_km_s", "vz_km_s"])
        for i, name in enumerate(names):
            writer.writerow(
                [
                    name,
                    systems[i],
                    f"{mu[i]:.17e}",
                    "" if np.isnan(radius[i]) else f"{radius[i]:.17e}",
                    f"{positions[i, 0]:.17e}",
                    f"{positions[i, 1]:.17e}",
                    f"{positions[i, 2]:.17e}",
                    f"{velocities[i, 0]:.17e}",
                    f"{velocities[i, 1]:.17e}",
                    f"{velocities[i, 2]:.17e}",
                ]
            )


def write_close_approaches(
    path: Path,
    names: list[str],
    systems: list[str],
    radius: np.ndarray,
    pair_i: np.ndarray,
    pair_j: np.ndarray,
    min_pair_distance: np.ndarray,
    min_pair_step: np.ndarray,
    dt_days: float,
) -> None:
    known = ~np.isnan(radius[pair_i]) & ~np.isnan(radius[pair_j])
    clearance = np.full_like(min_pair_distance, np.nan)
    clearance[known] = min_pair_distance[known] - radius[pair_i][known] - radius[pair_j][known]
    sort_key = np.where(np.isnan(clearance), min_pair_distance, clearance)
    order = np.argsort(sort_key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["name_a", "name_b", "system_a", "system_b", "distance_km", "clearance_km", "time_days", "time_years"])
        for k in order:
            days = int(min_pair_step[k]) * dt_days
            writer.writerow(
                [
                    names[int(pair_i[k])],
                    names[int(pair_j[k])],
                    systems[int(pair_i[k])],
                    systems[int(pair_j[k])],
                    f"{min_pair_distance[k]:.17e}",
                    "" if np.isnan(clearance[k]) else f"{clearance[k]:.17e}",
                    f"{days:.9f}",
                    f"{days / JULIAN_YEAR_DAYS:.9f}",
                ]
            )


def write_monitor_summary(
    path: Path,
    monitors: list[dict],
    monitor_min: np.ndarray,
    monitor_max: np.ndarray,
    monitor_min_step: np.ndarray,
    monitor_max_step: np.ndarray,
    monitor_final: np.ndarray,
    dt_days: float,
) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["monitor", "min_km", "min_years", "max_km", "max_years", "final_km"])
        for i, monitor in enumerate(monitors):
            writer.writerow(
                [
                    monitor["name"],
                    f"{monitor_min[i]:.17e}",
                    f"{monitor_min_step[i] * dt_days / JULIAN_YEAR_DAYS:.9f}",
                    f"{monitor_max[i]:.17e}",
                    f"{monitor_max_step[i] * dt_days / JULIAN_YEAR_DAYS:.9f}",
                    f"{monitor_final[i]:.17e}",
                ]
            )


def closest_known_clearance(radius: np.ndarray, pair_i: np.ndarray, pair_j: np.ndarray, min_pair_distance: np.ndarray) -> tuple[int | None, float]:
    known = ~np.isnan(radius[pair_i]) & ~np.isnan(radius[pair_j])
    if not np.any(known):
        return None, float("nan")
    clearances = min_pair_distance[known] - radius[pair_i][known] - radius[pair_j][known]
    known_indices = np.flatnonzero(known)
    local = int(np.argmin(clearances))
    return int(known_indices[local]), float(clearances[local])


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run the independent Sol/Kcalbeloh N-body stability probe.")
    parser.add_argument("--data", type=Path, default=ROOT / "initial_state.json")
    parser.add_argument("--out-dir", type=Path, default=ROOT / "runs" / "current_1000yr_dt001")
    parser.add_argument("--years", type=float, default=1000.0)
    parser.add_argument("--dt-days", type=float, default=0.01)
    parser.add_argument("--report-every-years", type=float, default=1.0)
    parser.add_argument("--checkpoint-every-years", type=float, default=10.0)
    parser.add_argument("--track-every-steps", type=int, default=1)
    parser.add_argument("--resume", type=Path)
    parser.add_argument("--no-recenter", action="store_true")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    data = load_state(args.data)
    names, mu, radius, positions, velocities, systems = arrays_from_data(data)
    name_to_index = {name: i for i, name in enumerate(names)}
    monitors = data.get("monitors", [])
    pair_i, pair_j = np.triu_indices(len(names), 1)

    total_steps = int(round(args.years * JULIAN_YEAR_DAYS / args.dt_days))
    report_steps = max(1, int(round(args.report_every_years * JULIAN_YEAR_DAYS / args.dt_days)))
    checkpoint_steps = max(1, int(round(args.checkpoint_every_years * JULIAN_YEAR_DAYS / args.dt_days)))
    track_steps = max(1, args.track_every_steps)
    dt_seconds = args.dt_days * DAY_SECONDS

    args.out_dir.mkdir(parents=True, exist_ok=True)
    resume = args.resume is not None
    if resume:
        checkpoint = load_checkpoint(args.resume)
        step = int(checkpoint["step"])
        positions = checkpoint["positions"]
        velocities = checkpoint["velocities"]
        min_pair_distance = checkpoint["min_pair_distance"]
        min_pair_step = checkpoint["min_pair_step"]
        monitor_min = checkpoint["monitor_min"]
        monitor_max = checkpoint["monitor_max"]
        monitor_min_step = checkpoint["monitor_min_step"]
        monitor_max_step = checkpoint["monitor_max_step"]
        initial_energy = float(checkpoint["initial_energy"])
    else:
        step = 0
        if not args.no_recenter:
            recenter_total_barycentre(positions, velocities, mu)
        min_pair_distance = pair_distances(positions, pair_i, pair_j)
        min_pair_step = np.zeros_like(min_pair_distance, dtype=np.int64)
        monitor_initial = monitor_values(monitors, name_to_index, positions, mu) if monitors else np.array([], dtype=np.float64)
        monitor_min = monitor_initial.copy()
        monitor_max = monitor_initial.copy()
        monitor_min_step = np.zeros_like(monitor_min, dtype=np.int64)
        monitor_max_step = np.zeros_like(monitor_max, dtype=np.int64)
        initial_energy = total_energy(positions, velocities, mu, pair_i, pair_j)[0]
        ensure_metadata(args.out_dir, args.data, data, args, names)

    progress_handle, progress_writer = open_progress(args.out_dir / "progress.csv", resume)
    monitor_handle, monitor_writer = open_monitor_log(args.out_dir / "monitor_distances.csv", monitors, resume)

    acceleration_now = acceleration(positions, mu)
    start_wall = time.time()
    print(f"Loaded {len(names)} bodies from {args.data}")
    print(f"Years={args.years:g}; dt={args.dt_days:g} d; steps={total_steps:,}; starting_step={step:,}")
    print(f"Output: {args.out_dir}")

    try:
        while step < total_steps:
            velocities += 0.5 * dt_seconds * acceleration_now
            positions += dt_seconds * velocities
            acceleration_now = acceleration(positions, mu)
            velocities += 0.5 * dt_seconds * acceleration_now
            step += 1

            if step % track_steps == 0 or step == total_steps:
                distances = pair_distances(positions, pair_i, pair_j)
                better = distances < min_pair_distance
                min_pair_distance[better] = distances[better]
                min_pair_step[better] = step
                if monitors:
                    current_monitors = monitor_values(monitors, name_to_index, positions, mu)
                    lower = current_monitors < monitor_min
                    upper = current_monitors > monitor_max
                    monitor_min[lower] = current_monitors[lower]
                    monitor_max[upper] = current_monitors[upper]
                    monitor_min_step[lower] = step
                    monitor_max_step[upper] = step

            if step % report_steps == 0 or step == total_steps:
                years = step * args.dt_days / JULIAN_YEAR_DAYS
                energy, kinetic, potential = total_energy(positions, velocities, mu, pair_i, pair_j)
                drift = (energy - initial_energy) / abs(initial_energy) if initial_energy else 0.0
                sep = group_barycentre_distance(positions, mu, systems)
                closest_idx = int(np.argmin(min_pair_distance))
                clearance_idx, clearance = closest_known_clearance(radius, pair_i, pair_j, min_pair_distance)
                closest_pair = f"{names[int(pair_i[closest_idx])]}:{names[int(pair_j[closest_idx])]}"
                progress_writer.writerow(
                    [
                        step,
                        f"{years:.9f}",
                        f"{energy:.17e}",
                        f"{kinetic:.17e}",
                        f"{potential:.17e}",
                        f"{drift:.17e}",
                        f"{sep:.17e}",
                        closest_pair,
                        f"{min_pair_distance[closest_idx]:.17e}",
                        "" if clearance_idx is None else f"{clearance:.17e}",
                        f"{time.time() - start_wall:.3f}",
                    ]
                )
                progress_handle.flush()
                if monitors:
                    current_monitors = monitor_values(monitors, name_to_index, positions, mu)
                    for i, monitor in enumerate(monitors):
                        monitor_writer.writerow([step, f"{years:.9f}", monitor["name"], f"{current_monitors[i]:.17e}"])
                    monitor_handle.flush()
                print(f"{years:9.3f} yr  drift={drift:+.3e}  sep={sep:.6e} km  closest={closest_pair}")

            if step % checkpoint_steps == 0 or step == total_steps:
                years = step * args.dt_days / JULIAN_YEAR_DAYS
                latest = args.out_dir / "checkpoint_latest.npz"
                write_checkpoint(
                    latest,
                    step,
                    positions,
                    velocities,
                    min_pair_distance,
                    min_pair_step,
                    monitor_min,
                    monitor_max,
                    monitor_min_step,
                    monitor_max_step,
                    initial_energy,
                )
                write_checkpoint(
                    args.out_dir / f"checkpoint_{years:09.3f}yr.npz",
                    step,
                    positions,
                    velocities,
                    min_pair_distance,
                    min_pair_step,
                    monitor_min,
                    monitor_max,
                    monitor_min_step,
                    monitor_max_step,
                    initial_energy,
                )
                write_close_approaches(
                    args.out_dir / "close_approaches_latest.csv",
                    names,
                    systems,
                    radius,
                    pair_i,
                    pair_j,
                    min_pair_distance,
                    min_pair_step,
                    args.dt_days,
                )
                if monitors:
                    current_monitors = monitor_values(monitors, name_to_index, positions, mu)
                    write_monitor_summary(
                        args.out_dir / "monitor_summary_latest.csv",
                        monitors,
                        monitor_min,
                        monitor_max,
                        monitor_min_step,
                        monitor_max_step,
                        current_monitors,
                        args.dt_days,
                    )
    finally:
        progress_handle.close()
        monitor_handle.close()

    write_final_state(args.out_dir / "final_state.csv", names, systems, mu, radius, positions, velocities)
    write_close_approaches(
        args.out_dir / "close_approaches.csv",
        names,
        systems,
        radius,
        pair_i,
        pair_j,
        min_pair_distance,
        min_pair_step,
        args.dt_days,
    )
    if monitors:
        current_monitors = monitor_values(monitors, name_to_index, positions, mu)
        write_monitor_summary(
            args.out_dir / "monitor_summary.csv",
            monitors,
            monitor_min,
            monitor_max,
            monitor_min_step,
            monitor_max_step,
            current_monitors,
            args.dt_days,
        )
    print("Done.")


if __name__ == "__main__":
    main()
