package uz.yalla.sipphone.data.update.install

import java.nio.file.Path

interface InstallerContract {
    fun install(msiPath: Path, expectedSha256: String, logPath: Path)
}

fun MsiBootstrapperInstaller.asContract(): InstallerContract = object : InstallerContract {
    override fun install(msiPath: Path, expectedSha256: String, logPath: Path) {
        this@asContract.install(msiPath, expectedSha256, logPath)
    }
}
