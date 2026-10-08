# Frequently Asked Questions

## 1. Unable to Find Nearby Devices
CrossPaste uses DNS-SD (DNS Service Discovery) for service discovery, similar to how local network printing services are discovered. If service discovery isn't working correctly on your local network, there could be several reasons:

1. Firewall Blocking: Router or host firewalls may be blocking UDP port 5353, which is the standard port used by mDNS (multicast DNS) for service discovery. Firewalls might also be blocking multicast traffic, which DNS-SD relies on for communication.
2. Multicast Traffic Restrictions: Some routers might disable or limit multicast traffic by default, directly affecting DNS-SD functionality. The IGMP snooping feature on some routers may prevent multicast packets from being forwarded correctly.
3. Security Software Interference: Host security software (such as antivirus programs or third-party firewalls) might incorrectly identify DNS-SD traffic as a potential threat and block it.

You can verify if DNS-SD is working correctly using the following methods:

1. Start CrossPaste on each device.

2. Use command-line tools to check if the service can be discovered:

For Mac devices, execute:
```
dns-sd -B _crosspasteService._tcp
```

For Windows devices, first install Bonjour SDK from https://download.developer.apple.com/Developer_Tools/bonjour_sdk_for_windows_v3.0/bonjoursdksetup.exe, then run:
```
dns-sd -B _crosspasteService._tcp
```

For Linux devices, ensure you have the avahi-utils package installed. For Ubuntu, you can install it with:
```
sudo apt-get install avahi-utils
avahi-browse -r _crosspasteService._tcp
```

Note: Devices that have already been added to "My Devices" or blacklisted will not appear in nearby devices.
If the command-line tools can discover the service, but CrossPaste cannot, it may be an issue with CrossPaste itself. In this case, please submit an issue to us for further investigation.

## 2. Linux: Copies From Wayland Apps Are Not Captured

In a Wayland session CrossPaste reads the compositor's clipboard directly through the `ext-data-control-v1` / `wlr-data-control-unstable-v1` protocol, so copies made in native Wayland apps (including images, e.g. Spectacle screenshots) are captured. KDE Plasma, Sway, Hyprland, COSMIC, niri and other wlroots-based compositors support it.

GNOME (Mutter) does not implement these protocols. There CrossPaste falls back to the X11 clipboard that XWayland mirrors, which Mutter keeps in sync for every content type, so copies are still captured.

If a copy is not captured on another compositor, check `crosspaste.log` for `Compositor offers no data-control protocol`: the compositor is missing the protocol and CrossPaste is on the X11 fallback, which only sees what the compositor mirrors to XWayland.
