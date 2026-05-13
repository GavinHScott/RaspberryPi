# Boot and Scheduled Behaviour

This Raspberry Pi runs `SmartDeviceManager` from the repository rooted at:

```text
/home/gavinsco/apps
```

The application directory inside the repository is:

```text
/home/gavinsco/apps/SmartDeviceManager
```

The companion static UI directory is:

```text
/home/gavinsco/apps/SmartDeviceManagerUI
```

This Raspberry Pi's static LAN IP is:

```text
192.168.4.56
```

SmartDeviceManager listens on port `9090`. The UI listens one port higher, on port `9091`.

Access the UI from another device on the same network at:

```text
http://192.168.4.56:9091/
```

Access it from the Pi itself at:

```text
http://localhost:9091/
```

## Boot Behaviour

Boot behaviour is controlled by systemd units in:

```text
/etc/systemd/system/
```

Relevant units:

```text
/etc/systemd/system/smartdevicemanager-CheckForUpdates.service
/etc/systemd/system/smartdevicemanager.service
/etc/systemd/system/smartdevicemanager-ui.service
```

`smartdevicemanager-CheckForUpdates.service` is a one-shot boot update-check service. It runs:

```text
/home/gavinsco/scripts/prepare-smartdevicemanager.sh
```

The update-check script:

1. Changes directory to `/home/gavinsco/apps`.
2. Checks out branch `main`.
3. Captures the current commit hash.
4. Runs `git pull --ff-only origin main`.
5. Captures the new commit hash.
6. Runs `mvn clean install` in `/home/gavinsco/apps/SmartDeviceManager` only if the commit hash changed.
7. Verifies that `target/SmartDeviceManager-1.0.0.jar` exists.

`smartdevicemanager.service` starts the built application from:

```text
/home/gavinsco/apps/SmartDeviceManager
```

It runs:

```text
/usr/bin/java -jar target/SmartDeviceManager-1.0.0.jar --server.port=9090
```

`smartdevicemanager-ui.service` starts the static UI from:

```text
/home/gavinsco/apps/SmartDeviceManagerUI
```

It runs:

```text
/usr/bin/python3 -m http.server 9091 --bind 0.0.0.0
```

The UI service is enabled for boot with:

```text
/etc/systemd/system/multi-user.target.wants/smartdevicemanager-ui.service
```

It is also linked to the main application lifecycle:

1. `smartdevicemanager.service` has `Wants=network-online.target smartdevicemanager-ui.service`.
2. `smartdevicemanager-ui.service` has `After=network-online.target smartdevicemanager.service`.
3. `smartdevicemanager-ui.service` has `PartOf=smartdevicemanager.service`, so stopping SmartDeviceManager also stops the UI.

This means the UI starts on boot and is requested whenever SmartDeviceManager is started by systemd.

## Scheduled Behaviour

Scheduled OS behaviour is controlled by systemd timers in:

```text
/etc/systemd/system/
```

The daily graceful reboot schedule uses:

```text
/etc/systemd/system/smartdevicemanager-midnight-reboot.service
/etc/systemd/system/smartdevicemanager-midnight-reboot.timer
```

`smartdevicemanager-midnight-reboot.timer` runs daily at 00:01 local time with `Persistent=true`.

`smartdevicemanager-midnight-reboot.service` is a one-shot service that:

1. Stops `smartdevicemanager.service`.
2. Stops `smartdevicemanager-ui.service` indirectly through `PartOf=smartdevicemanager.service`.
3. Waits 5 seconds so the Logback startup-dated log file closes cleanly.
4. Reboots the Raspberry Pi.

After the reboot, systemd starts `smartdevicemanager-CheckForUpdates.service` before `smartdevicemanager.service`. That update-check service pulls the latest `main` branch from `/home/gavinsco/apps`. Backend changes are rebuilt with Maven when the commit changes. UI changes do not need a build step because the UI is static files served directly from `/home/gavinsco/apps/SmartDeviceManagerUI`.

So the midnight path is:

```text
00:01 timer -> stop SmartDeviceManager -> UI stops with it -> reboot -> pull latest main -> rebuild backend if needed -> start backend on 9090 -> start UI on 9091
```

SmartDeviceManager writes to a log file named from the date the application process started:

```text
/home/gavinsco/apps/SmartDeviceManager/logs/smart-device-manager-DD-MM-YYYY.log
```

The application does not roll to a new dated file at midnight. If the process started on one day and runs past midnight, it continues writing to the original startup date's file until systemd stops it. After the 00:01 reboot, the boot-time service start creates or appends to the new day's log file.

The old cron reboot file has been removed and should not be recreated:

```text
/etc/cron.d/smartdevicemanager-midnight-reboot
```

The user crontab should not run SmartDeviceManager update scripts. The old scheduled update script has been removed.

## In-App Scheduled Behaviour

The Spring application also has an internal scheduled task:

```text
/home/gavinsco/apps/SmartDeviceManager/src/main/java/com/SmartDeviceManager/service/ScheduledTasks.java
```

That task runs inside the application process using Spring's `@Scheduled` annotation.

## Verification Notes

The boot and update wiring was verified by inspecting the installed systemd files and enabled symlinks, not by starting services or smoke-testing endpoints.

Verified files:

```text
/etc/systemd/system/smartdevicemanager.service
/etc/systemd/system/smartdevicemanager-ui.service
/etc/systemd/system/smartdevicemanager-CheckForUpdates.service
/etc/systemd/system/smartdevicemanager-midnight-reboot.service
/etc/systemd/system/smartdevicemanager-midnight-reboot.timer
/home/gavinsco/scripts/prepare-smartdevicemanager.sh
```

Verified enabled links:

```text
/etc/systemd/system/multi-user.target.wants/smartdevicemanager.service
/etc/systemd/system/multi-user.target.wants/smartdevicemanager-ui.service
/etc/systemd/system/multi-user.target.wants/smartdevicemanager-CheckForUpdates.service
/etc/systemd/system/timers.target.wants/smartdevicemanager-midnight-reboot.timer
```
