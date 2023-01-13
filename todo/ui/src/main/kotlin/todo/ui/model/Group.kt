package todo.ui.model

class Group(id: Int) {
    var id = id
    var name = ""
    var notes = mutableListOf<Note>()
}