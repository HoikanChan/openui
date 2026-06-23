# BSP Restclient Offline Stub

This stub is only for local/offline SDK builds and integration tests when the
real BSP runtime artifact is unavailable.

Install it into the local Maven repository:

```powershell
packages\genui-java-sdk\bsp-stub\install-stub.ps1
```

It installs:

`com.huawei.bsp:com.huawei.bsp.commonlib.resetclient:25.590.54`

The artifact name intentionally follows the currently confirmed coordinate,
including the `resetclient` spelling. Replace this stub with the real BSP
dependency in BSP runtime builds.
