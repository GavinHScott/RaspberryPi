# Sol/Kcalbeloh Long Stability Run

This directory is self-contained for the Raspberry Pi. It does not need the KSP
install once `initial_state.json` has been copied here.

## Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
```

## Recommended 1000 Year Run

```bash
python3 pi_long_sim.py --years 1000 --dt-days 0.01 --out-dir runs/current_1000yr_dt001
```

The script writes:

- `progress.csv`: energy drift, system separation, and closest pair so far.
- `monitor_distances.csv`: named pair/barycentre distances at every report.
- `checkpoint_latest.npz`: resumable state.
- `close_approaches_latest.csv`: closest approaches so far at each checkpoint.
- `monitor_summary_latest.csv`: min/max/final named monitor distances.
- `final_state.csv`, `close_approaches.csv`, and `monitor_summary.csv` when complete.

## Resume

```bash
python3 pi_long_sim.py \
  --years 1000 \
  --dt-days 0.01 \
  --out-dir runs/current_1000yr_dt001 \
  --resume runs/current_1000yr_dt001/checkpoint_latest.npz
```

## Notes

The integrator is a velocity-Verlet/leapfrog N-body probe in kilometres,
seconds, and km^3/s^2. It is intended as a stability filter for initial state
vectors, not as a replacement for Principia's integrator.
