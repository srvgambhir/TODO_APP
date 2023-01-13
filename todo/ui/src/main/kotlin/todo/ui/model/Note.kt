package todo.ui.model

class Note(id: Int, gid: Int) {
    var id = id
    var text = ""
    var priority = 3
    var gid = gid
    var last_edit: String = ""
    var due: String = ""
    var idx = -1
}
