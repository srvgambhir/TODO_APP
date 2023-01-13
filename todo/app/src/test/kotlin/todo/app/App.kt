package todo.app

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions
import org.springframework.boot.test.context.SpringBootTest
import java.sql.Connection
import kotlinx.serialization.json.*
import kotlinx.serialization.*
import todo.dtos.*

@SpringBootTest
class ToDoApplicationTests {
	val ToDoInst = TaskController()
	var conn: Connection? = null

	init {
		conn = ToDoInst.connect_test()
	}


	@Test
	fun userCreateTest() {
		var user = User("test", "test1")
		val result = ToDoInst.user_create(user, conn)
		var res = BaseResponse()
		res.status = 0
		res.error = "User already exists. Please choose a different username"
		Assertions.assertEquals(Json.encodeToString(listOf(res)), result)
	}

	@Test
	fun userAuthTest(){
		val result = ToDoInst.isValidUser("test", "test1", conn)
		var res = BaseResponse()
		res.status = 1
		res.message = "User authenticated"
		Assertions.assertEquals(Json.encodeToString(listOf(res)), result)
	}

	@Test
	fun validDateTest() {
		val result = ToDoInst.isValidDate("10/10/2025")
		Assertions.assertEquals(result, true)
	}

	@Test
	fun notvalidDateTest(){
		val result = ToDoInst.isValidDate("10/10/2020")
		Assertions.assertEquals(result, false)
	}

	@Test
	fun addTaskPriorityFailedTest() {
		val result = ToDoInst.addTask(Note(-1, "Test", 6, 3,"10/21/2022", "10/23/2025", 0))
		Assertions.assertEquals(0, Json.decodeFromString<List<BaseResponse>>(result)[0].status, )
	}

	@Test
	fun editTaskFailedTest() {
		val result = ToDoInst.editTask(Note(0, "EditTest", 1, 3,"10/22/2022", "10/23/2025", 0))
		Assertions.assertEquals(0, Json.decodeFromString<List<BaseResponse>>(result)[0].status)
	}

	@Test
	fun editTaskFailedPriorityTest() {
		val result = ToDoInst.editTask(Note(0, "EditTest", 5, 3, "10/22/2022", "10/23/2025", 0))
		Assertions.assertEquals(0, Json.decodeFromString<List<BaseResponse>>(result)[0].status)
	}

	@Test
	fun editTaskFailedDateTest() {
		val result = ToDoInst.editTask(Note(0, "EditTest", 5, 3,"10/22/2022", "10/23/2010", 0))
		Assertions.assertEquals(0, Json.decodeFromString<List<BaseResponse>>(result)[0].status)
	}

	@Test
	fun deleteTaskFailedTest() {
		val result = ToDoInst.deleteTask(10000)
		Assertions.assertEquals(0, Json.decodeFromString<List<BaseResponse>>(result)[0].status)
	}

	@Test
	fun noteQueryTest() {
		val result = ToDoInst.notes_query()
		val expected = listOf(mutableMapOf(
			"id" to "0",
			"text" to "EditTest",
			"priority" to "1",
			"gid" to "1",
			"last_edit" to "10/22/2022",
			"due" to "10/22/2024",
			"idx" to "0"
		))
		Assertions.assertEquals(expected, result)
	}

	@Test
	fun addGroupTest() {
		val result = Json.decodeFromString<List<BaseResponse>>(ToDoInst.addGroup(Group(-1, "AddTest")))[0]
		Assertions.assertEquals(1, result.status)
		ToDoInst.deleteGroup(result.message.toInt())
	}

	@Test
	fun groupQueryTest() {
		val result = ToDoInst.groups_query()
		val expected = listOf(mutableMapOf(
			"group_id" to "1",
			"group_name" to "TestGroup",
		))
		Assertions.assertEquals(expected, result)
	}

	@Test
	fun getTasksFromGroupTest() {
		val result = ToDoInst.getNotesFromGroup("1")
		val expected = listOf(mutableMapOf(
			"id" to "0",
			"text" to "EditTest",
			"priority" to "1",
			"gid" to "1",
			"last_edit" to "10/22/2022",
			"due" to "10/22/2024",
			"idx" to "0"
		))
		Assertions.assertEquals(expected, result)
	}
}
