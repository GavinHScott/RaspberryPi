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
/etc/systemd/system/smartdevicemanager-prepare.service
/etc/systemd/system/smartdevicemanager.service
```

`smartdevicemanager-prepare.service` is a one-shot boot preparation service. It runs:

```text
/home/gavinsco/scripts/prepare-smartdevicemanager.sh
```

The prepare script:

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

Scheduled OS behaviour is controlled by cron.

The midnight schedule is installed in:

```text
/etc/cron.d/smartdevicemanager-midnight-reboot
```

It reboots the device every day at midnight:

```text
0 0 * * * root /usr/sbin/reboot
```

The user crontab should not run SmartDeviceManager update scripts. The old scheduled update script is:

```text
/home/gavinsco/scripts/update-smartdevicemanager.sh
```

It is kept on disk for reference, but it is not part of the active schedule.

## In-App Scheduled Behaviour

The Spring application also has an internal scheduled task:

```text
/home/gavinsco/apps/SmartDeviceManager/src/main/java/com/gavos/SmartDeviceManager/service/ScheduledTasks.java
```

That task runs inside the application process using Spring's `@Scheduled` annotation.
