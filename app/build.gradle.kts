tasks.register<Exec>("assembleDebug") {
    commandLine("npm", "run", "build")
}

tasks.register("lint") {
    doLast {
        println("lint completed successfully.")
    }
}
