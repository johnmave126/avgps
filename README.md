# Ownship

Transferring GPS and barometric sensor telemetry to EFBs over GDL-90.

## Install

Download apk at [Releases](https://github.com/johnmave126/avgps/releases).

## Features
* GPS reading and satellite constellation information.
* Barometric pressure altitude reporting with altimeter calibration.
* Aircraft callsign configuration and broadcasting.
* Serve as GDL-90 data source for EFBs.
* Auto-detection of ForeFlight.
* Manually add and edit target EFB IP and port.

## Compatibility
* ForeFlight (tested)

## Screenshots
![Location Page](/images/screenshot_location.png)
![Satellite Page](/images/screenshot_satellite.png)

## Roadmap
- [x] Get a proper logo.
- [x] Manually add EFBs.
- [x] Barometer support.
- [x] Clean up code.
- [ ] Test other EFBs (help wanted).
- [ ] Get it on Google Play.

## Release Notes

### v2.0
* Update toolchain.
* Rebuilt UI with Jetpack Compose.
* Renamed app to Ownship with new app and notification icons.
* Added barometer monitoring and pressure altitude broadcasting.
* Added callsign setting and broadcasting.
* Added manual EFB IP and port target configuration.
