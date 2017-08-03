# Samba Documents Provider
## Overview
This is an Android app that extends the built in File Manager to support connecting to SMB
file shares.

This app is built on top of Samba 4.5.1.

## Setup
### Prerequisite
Android SDK and NDK r15b or above are required to build this app. Android Studio is highly
recommended.

This build guide is only tested on Ubuntu. Changes to make it build on other platforms are
welcome.

### Build Steps
1. Download and unarchive Samba 4.5.1 [source code][samba-source].
2. Change directory to the root of Samba source code.
3. Create a git repository.
4. Run `git apply <path_to_samba_documents_provider_source>/sambapatch.diff`.
5. Modify configure.sh to change $NDK to point to your NDK folder.
6. Uncomment corresponding flags in configure.sh to compile for different architecture.
   Uncomment flags for ARMv7 in addition to 32-bit ARM to compile it for ARMv7.
7. Run `configure.sh` to configure Samba project.
8. Run `compile.sh` to compile libsmbclient.so.
9. Run `install.sh <path_to_samba_documents_provider_source>/app/src/main/jniLibs/<ABI>`.
10. Change directory to SambaDocumentsProvider source code.
11. Run `mv app/src/main/jniLibs/<ABI>/includes app/src/main/cpp/samba_includes`.
12. Change directory to the root of Samba source code and run `make distclean`.
13. Repeat step 6-12 for all desired ABI's.
14. Make sure to change app's `build.gradle` to include only ABI's that Samba was built
    for in previous steps.
15. Compile SambaDocumentsProvider.

## Discussion
Please go to our [Google group][discussion] to discuss any issues.


[samba-source]: https://download.samba.org/pub/samba/stable/samba-4.5.1.tar.gz
[discussion]: https://groups.google.com/forum/#!forum/samba-documents-provider
