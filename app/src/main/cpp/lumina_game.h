#pragma once

namespace lumina {

/** Called once per frame by the native game bridge. */
void update(float deltaSeconds);

/** Returns the native runtime version. */
const char* version();

}
