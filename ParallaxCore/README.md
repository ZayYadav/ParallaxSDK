# ParallaxCore SDK

This module is the standalone ParallaxCore Android SDK.

## Public API

Consumer apps should use the stable facade instead of importing internal SDK packages directly:

```java
import com.Parallax.SDK.ParallaxSDK;
```

Activate the SDK with:

```java
ParallaxSDK.activate("YOUR_USER_KEY");
```

Optional helpers exposed by the same import:

```java
boolean active = ParallaxSDK.isActivated();
String message = ParallaxSDK.getServerMessage();
```

The legacy internal APIs remain available for backwards compatibility.

## Build

Build the release AAR with:

```bash
./gradlew :ParallaxCore:assembleRelease
```

Output:

`ParallaxCore/build/outputs/aar/ParallaxCore-release.aar`

The branch intentionally contains no loader application or web panel. Existing deployed backend/trust URLs remain unchanged where compatibility requires them.
