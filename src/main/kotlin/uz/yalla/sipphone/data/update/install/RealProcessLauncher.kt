package uz.yalla.sipphone.data.update.install

class RealProcessLauncher : ProcessLauncher {
    override fun launch(command: List<String>) {
        ProcessBuilder(command)
            .inheritIO()
            .start()
    }
}
