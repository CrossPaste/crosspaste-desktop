package com.crosspaste.os.windows.api

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HICON
import com.sun.jna.platform.win32.WinDef.MAX_PATH
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.win32.W32APIOptions

internal interface Shell32 : com.sun.jna.platform.win32.Shell32 {
    /**
     * Used by SHGetStockIconInfo to identify which stock system icon to retrieve.
     *
     *
     * See https://docs.microsoft.com/en-us/windows/win32/api/shellapi/ne-shellapi-shstockiconid
     */
    interface SHSTOCKICONID {
        companion object {
            const val SIID_DOCNOASSOC: Int = 0
            const val SIID_DOCASSOC: Int = 1
            const val SIID_APPLICATION: Int = 2
            const val SIID_FOLDER: Int = 3
            const val SIID_FOLDEROPEN: Int = 4
            const val SIID_DRIVE525: Int = 5
            const val SIID_DRIVE35: Int = 6
            const val SIID_DRIVEREMOVE: Int = 7
            const val SIID_DRIVEFIXED: Int = 8
            const val SIID_DRIVENET: Int = 9
            const val SIID_DRIVENETDISABLED: Int = 10
            const val SIID_DRIVECD: Int = 11
            const val SIID_DRIVERAM: Int = 12
            const val SIID_WORLD: Int = 13
            const val SIID_SERVER: Int = 15
            const val SIID_PRINTER: Int = 16
            const val SIID_MYNETWORK: Int = 17
            const val SIID_FIND: Int = 22
            const val SIID_HELP: Int = 23
            const val SIID_SHARE: Int = 28
            const val SIID_LINK: Int = 29
            const val SIID_SLOWFILE: Int = 30
            const val SIID_RECYCLER: Int = 31
            const val SIID_RECYCLERFULL: Int = 32
            const val SIID_MEDIACDAUDIO: Int = 40
            const val SIID_LOCK: Int = 47
            const val SIID_AUTOLIST: Int = 49
            const val SIID_PRINTERNET: Int = 50
            const val SIID_SERVERSHARE: Int = 51
            const val SIID_PRINTERFAX: Int = 52
            const val SIID_PRINTERFAXNET: Int = 53
            const val SIID_PRINTERFILE: Int = 54
            const val SIID_STACK: Int = 55
            const val SIID_MEDIASVCD: Int = 56
            const val SIID_STUFFEDFOLDER: Int = 57
            const val SIID_DRIVEUNKNOWN: Int = 58
            const val SIID_DRIVEDVD: Int = 59
            const val SIID_MEDIADVD: Int = 60
            const val SIID_MEDIADVDRAM: Int = 61
            const val SIID_MEDIADVDRW: Int = 62
            const val SIID_MEDIADVDR: Int = 63
            const val SIID_MEDIADVDROM: Int = 64
            const val SIID_MEDIACDAUDIOPLUS: Int = 65
            const val SIID_MEDIACDRW: Int = 66
            const val SIID_MEDIACDR: Int = 67
            const val SIID_MEDIACDBURN: Int = 68
            const val SIID_MEDIABLANKCD: Int = 69
            const val SIID_MEDIACDROM: Int = 70
            const val SIID_AUDIOFILES: Int = 71
            const val SIID_IMAGEFILES: Int = 72
            const val SIID_VIDEOFILES: Int = 73
            const val SIID_MIXEDFILES: Int = 74
            const val SIID_FOLDERBACK: Int = 75
            const val SIID_FOLDERFRONT: Int = 76
            const val SIID_SHIELD: Int = 77
            const val SIID_WARNING: Int = 78
            const val SIID_INFO: Int = 79
            const val SIID_ERROR: Int = 80
            const val SIID_KEY: Int = 81
            const val SIID_SOFTWARE: Int = 82
            const val SIID_RENAME: Int = 83
            const val SIID_DELETE: Int = 84
            const val SIID_MEDIAAUDIODVD: Int = 85
            const val SIID_MEDIAMOVIEDVD: Int = 86
            const val SIID_MEDIAENHANCEDCD: Int = 87
            const val SIID_MEDIAENHANCEDDVD: Int = 88
            const val SIID_MEDIAHDDVD: Int = 89
            const val SIID_MEDIABLURAY: Int = 90
            const val SIID_MEDIAVCD: Int = 91
            const val SIID_MEDIADVDPLUSR: Int = 92
            const val SIID_MEDIADVDPLUSRW: Int = 93
            const val SIID_DESKTOPPC: Int = 94
            const val SIID_MOBILEPC: Int = 95
            const val SIID_USERS: Int = 96
            const val SIID_MEDIASMARTMEDIA: Int = 97
            const val SIID_MEDIACOMPACTFLASH: Int = 98
            const val SIID_DEVICECELLPHONE: Int = 99
            const val SIID_DEVICECAMERA: Int = 100
            const val SIID_DEVICEVIDEOCAMERA: Int = 101
            const val SIID_DEVICEAUDIOPLAYER: Int = 102
            const val SIID_NETWORKCONNECT: Int = 103
            const val SIID_INTERNET: Int = 104
            const val SIID_ZIPFILE: Int = 105
            const val SIID_SETTINGS: Int = 106
            const val SIID_DRIVEHDDVD: Int = 132
            const val SIID_DRIVEBD: Int = 133
            const val SIID_MEDIAHDDVDROM: Int = 134
            const val SIID_MEDIAHDDVDR: Int = 135
            const val SIID_MEDIAHDDVDRAM: Int = 136
            const val SIID_MEDIABDROM: Int = 137
            const val SIID_MEDIABDR: Int = 138
            const val SIID_MEDIABDRE: Int = 139
            const val SIID_CLUSTEREDDRIVE: Int = 140
            const val SIID_MAX_ICONS: Int = 175
        }
    }

    /**
     * Provides a default handler to extract an icon from a file.
     *
     * See <a>https://docs.microsoft.com/en-us/windows/win32/api/shlobj_core/nf-shlobj_core-shdefextracticona</a>
     *
     * @param pszIconFile A pointer to a null-terminated buffer that contains the path and name of the file from which the icon is extracted.
     * @param iIndex      The location of the icon within the file named in pszIconFile.
     * If this is a positive number, it refers to the zero-based position of the icon in the file.
     * For instance, 0 refers to the 1st icon in the resource file and 2 refers to the 3rd.
     * If this is a negative number, it refers to the icon's resource ID.
     * @param uFlags      A flag that controls the icon extraction.
     * GIL_SIMULATEDOC: Overlays the extracted icon on the default document icon to create the final icon.
     * This icon can be used when no more appropriate icon can be found or retrieved.
     * @param phiconLarge A pointer to an HICON that, when this function returns successfully,
     * receives the handle of the large version of the icon specified in the
     * LOWORD of nIconSize. This value can be NULL.
     * @param phiconSmall A pointer to an HICON that, when this function returns successfully, receives the handle of
     * the small version of the icon specified in the HIWORD of nIconSize.
     * @param nIconSize   A value that contains the large icon size in its LOWORD and the small icon size in its HIWORD.
     * Size is measured in pixels. Pass 0 to specify default large and small sizes.
     * @return This function can return one of these values.
     * - [com.sun.jna.platform.win32.WinError.S_OK]: Success.
     * - [com.sun.jna.platform.win32.WinError.S_FALSE]: The requested icon is not present.
     * - [com.sun.jna.platform.win32.WinError.E_FAIL]: The file cannot be accessed, or is being accessed through a slow link.
     */
    fun SHDefExtractIcon(
        pszIconFile: String?,
        iIndex: Int,
        uFlags: Int,
        phiconLarge: Array<HICON?>?,
        phiconSmall: Array<HICON?>?,
        nIconSize: Int,
    ): Int

    /**
     * Retrieves information about an object in the file system, such as a file, folder, directory, or drive root.
     *
     * See <a>https://docs.microsoft.com/en-us/windows/win32/api/shellapi/nf-shellapi-shgetfileinfoa</a>
     *
     * @param pszPath          A pointer to a null-terminated string of maximum length MAX_PATH that contains the path and
     * file name. Both absolute and relative paths are valid.
     *
     *
     * If the uFlags parameter includes the SHGFI_PIDL flag, this parameter must be the address
     * of an ITEMIDLIST (PIDL) structure that contains the list of item identifiers that
     * uniquely identifies the file within the Shell's namespace. The PIDL must be a fully
     * qualified PIDL. Relative PIDLs are not allowed.
     *
     *
     * If the uFlags parameter includes the SHGFI_USEFILEATTRIBUTES flag, this parameter does
     * not have to be a valid file name.
     * The function will proceed as if the file exists with the specified name and with the
     * file attributes passed in the dwFileAttributes parameter.
     * This allows you to obtain information about a file type by passing just the extension for
     * pszPath and passing FILE_ATTRIBUTE_NORMAL in dwFileAttributes.
     *
     *
     * This string can use either short (the 8.3 form) or long file names.
     * @param dwFileAttributes A combination of one or more file attribute flags (FILE_ATTRIBUTE_ values as defined
     * in [com.sun.jna.platform.win32.WinNT]).
     * If uFlags does not include the SHGFI_USEFILEATTRIBUTES flag, this parameter is ignored.
     * @param psfi             Pointer to a SHFILEINFO structure to receive the file information.
     * @param cbFileInfo       The size, in bytes, of the SHFILEINFO structure pointed to by the psfi parameter.
     * @param uFlags           The flags that specify the file information to retrieve (see SHGFI_ values).
     * @return Returns a value whose meaning depends on the uFlags parameter.
     *
     *
     * If uFlags does not contain SHGFI_EXETYPE or SHGFI_SYSICONINDEX, the return value is nonzero if successful, or zero otherwise.
     *
     *
     * If uFlags contains the SHGFI_EXETYPE flag, the return value specifies the type of the executable file.
     * It will be one of the following values.
     * - 0: Nonexecutable file or an error condition.
     * - LOWORD = NE or PE and HIWORD = Windows version: Windows application.
     * - LOWORD = MZ and HIWORD = 0: MS-DOS .exe or .com file
     * - LOWORD = PE and HIWORD = 0: Console application or .bat file
     */
    fun SHGetFileInfo(
        pszPath: String?,
        dwFileAttributes: Int,
        psfi: SHFILEINFO?,
        cbFileInfo: Int,
        uFlags: Int,
    ): Int

    @Structure.FieldOrder(
        "hIcon",
        "iIcon",
        "dwAttributes",
        "szDisplayName",
        "szTypeName",
    )
    class SHFILEINFO : Structure(), Structure.ByReference {
        @JvmField var hIcon: HICON? = null

        @JvmField var iIcon: Int = 0

        @JvmField var dwAttributes: DWORD? = null

        @JvmField var szDisplayName: CharArray = CharArray(MAX_PATH)

        @JvmField var szTypeName: CharArray = CharArray(80)
    }

    /**
     * Retrieves information about system-defined Shell icons.
     *
     * See <a>https://docs.microsoft.com/en-us/windows/win32/api/shellapi/nf-shellapi-shgetstockiconinfo</a>
     *
     * @param siid One of the values from the [SHSTOCKICONID] enumeration that specifies which icon should be retrieved.
     * @param uFlags A combination of zero or more of the flags that specify which information is requested (SHGSI_ values).
     * @return If this function succeeds, it returns S_OK. Otherwise, it returns an HRESULT error code.
     */
    fun SHGetStockIconInfo(
        siid: Int,
        uFlags: Int,
        psii: SHSTOCKICONINFO?,
    ): HRESULT?

    @Structure.FieldOrder(
        "cbSize",
        "hIcon",
        "iSysImageIndex",
        "iIcon",
        "szDisplayName",
    )
    class SHSTOCKICONINFO : Structure(), Structure.ByReference {
        @JvmField var cbSize: DWORD? = null

        @JvmField var hIcon: HICON? = null

        @JvmField var iSysImageIndex: Int = 0

        @JvmField var iIcon: Int = 0

        @JvmField var szDisplayName: CharArray = CharArray(MAX_PATH)
    }

    companion object {
        val INSTANCE: Shell32 =
            Native.load("shell32", Shell32::class.java, W32APIOptions.DEFAULT_OPTIONS)

        /**
         * Indicates that the function should not attempt to access the file specified by pszPath.
         * Rather, it should act as if the file specified by pszPath exists with the file attributes passed in
         * dwFileAttributes.
         * This flag cannot be combined with the SHGFI_ATTRIBUTES, SHGFI_EXETYPE, or SHGFI_PIDL flags.
         */
        const val SHGFI_USEFILEATTRIBUTES: Int = 0x000000010

        /**
         * Retrieve the name of the file that contains the icon representing the file specified by pszPath,
         * as returned by the IExtractIcon::GetIconLocation method of the file's icon handler.
         * Also retrieve the icon index within that file.
         * The name of the file containing the icon is copied to the szDisplayName member of the structure specified by psfi.
         * The icon's index is copied to that structure's iIcon member.
         */
        const val SHGFI_ICONLOCATION: Int = 0x000001000

        /**
         * The szPath and iIcon members of the SHSTOCKICONINFO structure receive the path and icon index of the requested
         * icon, in a format suitable for passing to the ExtractIcon function.
         * The numerical value of this flag is zero, so you always get the icon location regardless of other flags.
         */
        const val SHGSI_ICONLOCATION: Int = 0
    }
}
