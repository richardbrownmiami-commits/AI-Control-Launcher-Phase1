#include <stdint.h>

// Deliberate ARMv7a native payload used to enforce and verify the launcher ABI.
// No external native library is required.
int32_t launcher_abi_guard(void) {
    return 32;
}
