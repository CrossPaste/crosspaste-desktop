package com.crosspaste.paste

import com.crosspaste.config.CommonConfigManager

class DesktopSourceExclusionService(
    override val configManager: CommonConfigManager,
) : SourceExclusionService
