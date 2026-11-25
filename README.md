# DUALNET

Native Android network analyzer for checking Wi-Fi and cellular availability and observing per-interface traffic rates.

## First milestone
- Observe internet-capable Wi-Fi and cellular networks through `ConnectivityManager`.
- Show transport, interface name, metered state and Android's estimated downstream bandwidth.
- Sample interface RX/TX byte counters every second on Android 12+.
- Build a debug APK with GitHub Actions.

## Important measurement limits
`TrafficStats.getRxBytes(interface)` and `getTxBytes(interface)` are partial interface counters. They are useful for observing traffic on a particular interface, but Android documents that they are not complete or fully adjusted accounting. Some OEM builds may return unsupported values or hide interfaces. The displayed sum is therefore an observed estimate, not a speed-test result and not proof that a single app's traffic is bonded.

The analyzer does not request a cellular network, change the system default route, create a VPN, or combine traffic. It passively observes networks that Android exposes to the app.

## Build
Use GitHub Actions: push to `main`, then download the `DUALNET-debug-apk` artifact from the workflow run.

Package: `com.rakesh.dualnet`
