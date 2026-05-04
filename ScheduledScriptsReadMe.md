# Boot and Scheduled Behaviour

This Raspberry Pi runs `SmartDeviceManager` from the repository rooted at:

```text
/home/gavinsco/apps
```

The application directory inside the repository is:

```text
/home/gavinsco/apps/SmartDeviceManager
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
2. Waits 5 seconds so the Logback startup-dated log file closes cleanly.
3. Reboots the Raspberry Pi.

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
/home/gavinsco/apps/SmartDeviceManager/src/main/java/com/gavos/SmartDeviceManager/service/ScheduledTasks.java
```

That task runs inside the application process using Spring's `@Scheduled` annotation.
