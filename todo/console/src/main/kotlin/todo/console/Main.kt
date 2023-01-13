package todo.console

import io.ktor.http.*
import java.math.BigInteger
import java.security.MessageDigest
import kotlinx.coroutines.*

private fun md5(input:String): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
}

fun main(args: Array<String>) = runBlocking<Unit> {
    println("************************************")
    println("*****    WELCOME TO TRACKIT   ******")
    println("************************************\n")

    println("I am your personal TODO List.\n")

    var haveAccount = false

    while (true) {
        print("Do you have an account with us? (y/n): ")
        val userInputAccount = readLine()!!.lowercase()
        if (userInputAccount == "y") {
            haveAccount = true
            break;
        } else if (userInputAccount == "n") {
            break;
        }
        print("\n")
    }

    if (haveAccount) {
        while(true) {
            print("Please enter your username: ")
            val username = readLine()!!
            print("Please enter your password: ")
            val password = readLine()!!
            val hashedPassword = md5(password)

            val response = (async { HttpRequest.logInUser(username, hashedPassword) }).await()
            if (response.status == 1) {
                break
            } else {
                println("There was an error accessing your account: " + response.error)
                println("Please try again.")
            }
        }
    } else {
        while(true) {
            print("Let's set up an account. Please enter a preferred username: ")
            val username = readLine()!!
            print("Please enter a password: ")
            val password = readLine()!!
            val hashedPassword = md5(password)

            val response = (async { HttpRequest.signUpUser(username, hashedPassword) }).await()
            if (response.status == 1) {
                break
            } else {
                println("There was an error creating your account: " + response.error)
                println("Please try again.")
            }
        }
    }

    println("\nAwesome, you're in!")
    println("Please type help to learn prompts you can use.\n")
    var local = (async { HttpRequest.getTasks() }).await()
    while(true) {
        val command = readLine()!!

        if (command == "quit") {
            break;
        } else if (command == "help") {
            println("Here are the commands you can use:")
            println("help                  - get a list of available commands")
            println("list                  - view all your tasks in your to-do list")
            println("add [item]            - add item with description [item] to your list")
            println("delete [num]          - delete the to-do list item corresponding to [num]")
            println("edit [num] [new-item] - edit the to-do list item [num] to be [new-item]")
            println("quit                  - quit the console application\n")
        } else if (command.startsWith("list")) {
            if (local.isEmpty()) {
                println("You currently have no items to be tracked!\n")
                continue
            }
            local.forEachIndexed { i, item ->
                println((i + 1).toString() + ". " + item.get("text").orEmpty())
            }

            println()
        } else if (command.startsWith("add")) {
            val item = command.split(" ", limit=2)[1]

            val response = (async { HttpRequest.addTask(item, -1, -1, "") }).await()

            if (response.status != 1) {
                println("There was an error adding that item: " + response.error)
                continue
            } else {
                println("Item created!\n")
            }

            local = (async { HttpRequest.getTasks() }).await()
        } else if (command.startsWith("delete")) {
            val idxToDelete = command.split(" ", limit=2)[1].toInt()
            val response = (async { HttpRequest.deleteTask(local[idxToDelete - 1].get("id").orEmpty()) }).await()

            if (response.status != HttpStatusCode.OK) {
                println("There was an error deleting that item.")
                continue
            } else {
                println("Item deleted!\n")
            }

            local = (async { HttpRequest.getTasks() }).await()
        } else if (command.startsWith("edit")) {
            val split1 = command.split(" ", limit=2)[1]
            val idxToEdit = split1.split(" ", limit=2)[0].toInt()
            val newItem = split1.split(" ", limit=2)[1]

            val response = (async { HttpRequest.editTask(local[idxToEdit - 1].get("id").orEmpty(), newItem, -1, -1, "") }).await()

            if (response.status != 1) {
                println("There was an error editing that item: " + response.error)
                continue
            } else {
                println("Item edited!\n")
            }

            local = (async { HttpRequest.getTasks() }).await()
        } else {
            println("Sorry! Command not recognized, please try again.")
        }
    }
}
