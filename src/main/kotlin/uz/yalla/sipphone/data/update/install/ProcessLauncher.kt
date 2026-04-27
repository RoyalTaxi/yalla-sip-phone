package uz.yalla.sipphone.data.update.install

interface ProcessLauncher {
    fun launch(command: List<String>)
}
