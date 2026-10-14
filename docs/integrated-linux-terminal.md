# Integrated Linux terminal

The app's terminal route is now an app-private Linux workspace. It downloads the official Alpine ARMv7 minirootfs on first use, verifies its SHA-256 digest, validates archive paths before extraction, preserves the extracted rootfs under `files/linux-runtime/alpine-armv7`, and configures Alpine's matching `latest-stable` repositories.

The runtime uses the Termux-maintained PRoot source, pinned to commit `b8c1f5c5c6730dc9d521d5bd5ce1cfeedecde087`. That revision contains the `link2symlink` extension and the `--link2symlink` command-line option. The app will not substitute Android's host shell and will remain non-ready until the verified `armeabi-v7a` executable is placed at `files/linux-runtime/proot-armv7`.

## ARM32 build gate

The PRoot executable must be built with an Android NDK clang toolchain, not a glibc `arm-linux-gnueabihf-gcc` toolchain. The reviewed PRoot makefile links `talloc`; the build environment must provide an Android-compatible talloc dependency or a demonstrably compatible static build. The final binary must be inspected with `file`, `readelf -l`, `readelf -d`, and `readelf -s`, and must be launched on the Android 9 ARM32 device before the runtime is called ready.

A minimum review checklist is:

```text
git clone https://github.com/termux/proot
cd proot
git checkout b8c1f5c5c6730dc9d521d5bd5ce1cfeedecde087
grep -R --line-number link2symlink src
make -f src/GNUmakefile CC="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang --target=armv7a-linux-androideabi28" STRIP="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
file src/proot
readelf -l src/proot
readelf -d src/proot
src/proot --help | grep -- --link2symlink
```

The exact linker flags and dependency strategy must be recorded with the produced artifact. A successful host compilation or `file` output alone is not sufficient evidence of Android compatibility.

## Runtime acceptance

After the executable and rootfs are installed on a physical Samsung Galaxy M10 SM-M105F, run these commands inside the app and retain their output and exit codes:

```sh
apk --version
apk update
apk add bash python3 git
apk info
echo USMAN_AND_UMAR_TERMINAL_OK
pwd
ls
id
uname -m
bash --version
python3 --version
git --version
```

Also create, modify, rename, link, and delete files; close and reopen the app; and confirm the home directory remains intact. Network failures must be reported separately from PRoot or Alpine failures.
