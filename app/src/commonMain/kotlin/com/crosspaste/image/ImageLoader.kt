package com.crosspaste.image

import com.crosspaste.utils.Loader
import okio.Path

interface FaviconLoader : Loader<String, Path>

interface FileExtImageLoader : Loader<Path, Path>
