package com.jetbrains.pff.calendar.server

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ReceiveChannel
import java.time.LocalDate
import java.time.Period
import java.util.*
import kotlin.math.abs


fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused")
fun Application.module() {
    install(WebSockets)
    routing {
        val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())
        webSocket("/calendar") {
            println("Adding user!")
            val thisConnection = Connection(this)
            connections += thisConnection
            try {
                send("Usage:\n" +
                        "Use one of commands:\n" +
                        "\"check\" to check is year leap\n" +
                        "\"calc\" to calc interval length\n" +
                        "\"day\" to get the name of day of week\n" +
                        "\"quit\" to exit")
                send("Input the command:")
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    when (frame.readText()){
                        "check" -> checkFunction(thisConnection, incoming)
                        "calc" -> calcFunction(thisConnection, incoming)
                        "day" -> dayFunction(thisConnection, incoming)
                        "quit" -> quitFunction(thisConnection, connections)
                        else -> thisConnection.session.send("Unknown command. Choose one of the following to proceed:\n" +
                                "check | calc | day | quit")
                    }
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
            } finally {
                quitFunction(thisConnection, connections)
            }
        }
    }
}

suspend fun checkFunction(connection: Connection, incoming : ReceiveChannel<Frame>){
    connection.session.send("Input the year")
    for (frame in incoming) {
        frame as? Frame.Text ?: continue
        val receivedText = frame.readText()
        try {
            val receivedNumber = receivedText.toInt()
            connection.session.send("Is year $receivedNumber leap? ${isLeapYear(receivedNumber)}")
            connection.session.send("----\n" +
                    "Input the command:")
            return
        }catch (e:NumberFormatException){
            connection.session.send("You must send a valid number\n" +
                    "Input the year")
        }
    }
}
/*
год, номер которого кратен 400, — високосный;
остальные годы, номер которых кратен 100, — невисокосные (например, годы 1700, 1800, 1900, 2100, 2200, 2300);
остальные годы, номер которых кратен 4, — високосные[5].
все остальные годы — невисокосные.
 */
fun isLeapYear(year : Int) : Boolean{
    if (year % 400 == 0) return true
    if (year % 100 == 0) return false
    if (year % 4 == 0) return true
    return false
}

suspend fun calcFunction(connection: Connection, incoming : ReceiveChannel<Frame>){
    val clientDate = getDateFromClient(connection, incoming)
    val receivedYear = clientDate.year
    val receivedMonth = clientDate.month
    val receivedDay = clientDate.day
    val clientDate2 = getDateFromClient(connection, incoming)
    val receivedYear2 = clientDate2.year
    val receivedMonth2 = clientDate2.month
    val receivedDay2 = clientDate2.day
    if (checkDate(clientDate) and checkDate(clientDate2)){
        val date1 = LocalDate.of(receivedYear, receivedMonth, receivedDay)
        val date2 = LocalDate.of(receivedYear2, receivedMonth2, receivedDay2)
        var period = Period.between(date1, date2)
        connection.session.send("Inerval between dates is ${abs(period.years)} year(s) ${abs(period.months)} month(s) ${abs(period.days)} day(s)")
        connection.session.send("----\n" +
                "Input the command:")
        return
    }
    else{
        connection.session.send("Incorrect date, can't find interval")
        connection.session.send("----\n" +
                "Input the command:")
        return
    }
}

suspend fun dayFunction(connection: Connection, incoming : ReceiveChannel<Frame>){
    val clientDate = getDateFromClient(connection, incoming)
    val receivedYear = clientDate.year
    val receivedMonth = clientDate.month
    val receivedDay = clientDate.day
    if (checkDate(clientDate)){
        val date = LocalDate.of(receivedYear, receivedMonth, receivedDay)
        connection.session.send("${date.dayOfWeek}")
        connection.session.send("----\n" +
                "Input the command:")
        return
    }
    else{
        connection.session.send("Incorrect date, can't find day of week")
        connection.session.send("----\n" +
                "Input the command:")
        return
    }

}

data class ClientDate(val year: Int, val month: Int, val day: Int)

suspend fun getDateFromClient(connection: Connection, incoming : ReceiveChannel<Frame>) : ClientDate {
    var receivedYear = 0
    var receivedMonth = 0
    var receivedDay = 0
    connection.session.send("Input the year")
    for (frame in incoming) {
        frame as? Frame.Text ?: continue
        val receivedText = frame.readText()
        try {
            receivedYear = receivedText.toInt()
            break
        }catch (e:NumberFormatException){
            connection.session.send("You must send a valid number\n" +
                    "Input the year")
        }
    }
    connection.session.send("Input the month")
    for (frame in incoming) {
        frame as? Frame.Text ?: continue
        val receivedText = frame.readText()
        try {
            receivedMonth = receivedText.toInt()
            break
        }catch (e:NumberFormatException){
            connection.session.send("You must send a valid number\n" +
                    "Input the month")
        }
    }
    connection.session.send("Input the day")
    for (frame in incoming) {
        frame as? Frame.Text ?: continue
        val receivedText = frame.readText()
        try {
            receivedDay = receivedText.toInt()
            break
        }catch (e:NumberFormatException){
            connection.session.send("You must send a valid number\n" +
                    "Input the day")
        }
    }
    return ClientDate(receivedYear, receivedMonth, receivedDay)
}

fun checkDate(clientDate: ClientDate) : Boolean{
    return try {
        LocalDate.of(clientDate.year, clientDate.month, clientDate.day)
        true
    }catch (e : Exception){
        false
    }
}

fun quitFunction(connection: Connection, connections : MutableSet<Connection?>){
    println("Removing $connection!")
    connections -= connection
}
