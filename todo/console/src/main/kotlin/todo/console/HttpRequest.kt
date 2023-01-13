package todo.console

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import todo.dtos.*

object APIConstants {
    const val API_BASE_URL = "http://localhost:8080"
    const val POST_SIGNUP = "/api/add/user"
    const val GET_LOGIN = "/api/authenticate"
    const val ADD_TASK = "/api/add/task"
    const val EDIT_TASK = "/api/edit/task"
    const val DELETE_TASK = "/api/delete/task"
    const val GET_TASK = "/api/notes"
}



object HttpRequest {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
    }

    suspend fun signUpUser(username: String, hashedPass: String): BaseResponse {
        val response: HttpResponse = client.post(APIConstants.API_BASE_URL + APIConstants.POST_SIGNUP) {
            contentType(ContentType.Application.Json)
            setBody(User(username, hashedPass))
        }
        val body = Json.decodeFromString<List<BaseResponse>>(response.bodyAsText())
        return body[0]
    }

    suspend fun logInUser(username: String, hashedPass: String): BaseResponse {
        val response: HttpResponse = client.get(APIConstants.API_BASE_URL + APIConstants.GET_LOGIN) {
            url {
                parameters.append("username", username)
                parameters.append("password", hashedPass)
            }
        }
        val body = Json.decodeFromString<List<BaseResponse>>(response.bodyAsText())
        return body[0]
    }

    suspend fun getTasks(): List<MutableMap<String, String>> {
        val response: HttpResponse = client.get(APIConstants.API_BASE_URL + APIConstants.GET_TASK)
        return Json.decodeFromString<List<MutableMap<String, String>>>(response.bodyAsText())
    }

    suspend fun addTask(text: String, priority: Int, gid: Int, due: String): BaseResponse {
        val response: HttpResponse = client.post(APIConstants.API_BASE_URL + APIConstants.ADD_TASK) {
            contentType(ContentType.Application.Json)
            setBody(Note(text = text, priority = priority, gid = gid, due = due))
        }
        val body = Json.decodeFromString<List<BaseResponse>>(response.bodyAsText())
        return body[0]
    }

    suspend fun editTask(id: String, text: String, priority: Int, gid: Int, due: String): BaseResponse {
        val response: HttpResponse = client.put(APIConstants.API_BASE_URL + APIConstants.EDIT_TASK) {
            contentType(ContentType.Application.Json)
            setBody(Note(id = id.toInt(), text = text, priority = priority, gid = gid, due = due))
        }
        val body = Json.decodeFromString<List<BaseResponse>>(response.bodyAsText())
        return body[0]
    }

    suspend fun deleteTask(id: String): HttpResponse {
        val response: HttpResponse = client.delete(APIConstants.API_BASE_URL + APIConstants.DELETE_TASK + "/" + id)

        return response
    }
}