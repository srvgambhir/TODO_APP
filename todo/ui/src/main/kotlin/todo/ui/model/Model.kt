package todo.ui.model

import javafx.beans.InvalidationListener
import javafx.beans.Observable
import todo.ui.view.sideBar
import kotlinx.coroutines.*
import todo.ui.view.NoteView
import todo.ui.*
import java.util.function.Predicate
import java.util.Stack
import io.ktor.http.*

object Model: Observable {

    private val listeners = mutableListOf<InvalidationListener?>()

    var gidMappings : HashMap<String, Group>
            = HashMap<String, Group> ()

    var gidtogname : HashMap<Int, String> = HashMap<Int, String> ()

    var undo_stack =  Stack<Operation>()
    var redo_stack = Stack<Operation>()

    override fun addListener(listener: InvalidationListener?) {
        listeners.add(listener)
    }
    override fun removeListener(listener: InvalidationListener?) {
        listeners.remove(listener)
    }


    fun pushUndo(op_type: String, note: Note?, group: Group?){
        var new_op = Operation(op_type, note, group)
        undo_stack.push(new_op)
    }


    fun doOperations(poppedOp: Operation, stack: Stack<Operation>){
        println(poppedOp.op_type)
        if(poppedOp.op_type == "Add Note"){
            GlobalScope.launch(Dispatchers.IO) {
                val response = (async { HttpRequest.deleteTask(poppedOp.note!!.id) }).await()

                if (response.status != HttpStatusCode.OK) {
                    println("There was an error in deleting the note.")
                } else {
                    deleteNote(gidtogname.getOrDefault(poppedOp.note!!.gid, "Ungrouped"), poppedOp.note!!.id)
                    undo_stack.pop()
                    poppedOp.op_type = switchStack(poppedOp.op_type)
                    stack.push(poppedOp)
                }
            }
        }else if(poppedOp.op_type == "Delete Note"){
            GlobalScope.launch(Dispatchers.IO) {
                val response = (async { HttpRequest.addTask(poppedOp.note!!.text, poppedOp.note!!.priority, poppedOp.note!!.gid, poppedOp.note!!.due, poppedOp.note!!.idx) }).await()
                if (response.status != 1) {
                    println("There was an error editing that item: " + response.error)
                } else {
                    var note_id = response.message.toIntOrNull() ?: -1
                    var index = 0
                    val grpName = gidtogname.getOrDefault(poppedOp.note!!.gid, "Ungrouped")
                    if(gidMappings.containsKey(grpName)){
                        index =  Model.gidMappings[grpName]!!.notes.size
                    }
                    addNote(grpName, poppedOp.note!!.gid, note_id, poppedOp.note!!.text, poppedOp.note!!.priority, poppedOp.note!!.last_edit, poppedOp.note!!.due, index)
                    undo_stack.pop()
                    editNote(grpName, poppedOp.note!!.gid, poppedOp.note!!.gid, note_id, note_id, poppedOp.note!!.text, poppedOp.note!!.priority, poppedOp.note!!.last_edit, poppedOp.note!!.due, poppedOp.note!!.idx)
                    poppedOp.note!!.id = note_id
                    poppedOp.op_type = switchStack(poppedOp.op_type)
                    stack.push(poppedOp)
                }
            }
        }else if(poppedOp.op_type == "Add Group"){
            GlobalScope.launch(Dispatchers.IO) {
                if (poppedOp.grp!!.name != "Ungrouped") {
                    val deleteNotes = mutableListOf<Note>()
                    gidMappings[poppedOp.grp!!.name]!!.notes.forEach {
                        deleteNotes.add(it)
                    }
                    deleteNotes.forEach {
                        val response = (async { HttpRequest.deleteTask(it.id) }).await()
                        if (response.status != HttpStatusCode.OK) {
                            println("There was an error in deleting the note.")
                        } else {
                            deleteNote(poppedOp.grp!!.name, it.id)
                            undo_stack.pop()
                        }
                    }
                    val response = (async { HttpRequest.deleteGroup(poppedOp.grp!!.id) }).await()

                    if (!response.status.isSuccess()) {
                        println("There was an error in deleting the note.")
                    } else {
                        deleteGroup(poppedOp.grp!!.name)
                        undo_stack.pop()
                        sideBar.deleteLastGroup()
                        poppedOp.op_type = switchStack(poppedOp.op_type)
                        stack.push(poppedOp)
                    }
                }
            }
        }else if(poppedOp.op_type == "Delete Group"){

            GlobalScope.launch(Dispatchers.IO) {
                val response = (async { HttpRequest.addGroup(poppedOp.grp!!.name) }).await()
                var gid = -1
                if (response.status != 1) {
                    println("There was an error editing that group: " + response.error)
                } else {
                    println("Group added!\n")
                    gid = response.message.toIntOrNull() ?: -1
                    addGroup(poppedOp.grp!!.name, gid)
                    undo_stack.pop()
                    poppedOp.grp!!.id = gid
                    poppedOp.op_type = switchStack(poppedOp.op_type)
                    stack.push(poppedOp)
                }
                poppedOp.grp!!.notes.forEach{
                    val response = (async { HttpRequest.addTask(it.text, it.priority, it.gid, it.due, it.idx) }).await()
                    var note_id = response.message.toIntOrNull() ?: -1
                    if(response.status == 1){
                        addNote(poppedOp.grp!!.name, gid, note_id, it.text, it.priority, it.last_edit, it.due, it.idx)
                        undo_stack.pop()
                    }
                }
            }
        }else if(poppedOp.op_type == "Edit Note"){
            GlobalScope.launch(Dispatchers.IO) {
                val response = (async { HttpRequest.editTask(poppedOp.note!!.id, poppedOp.note!!.text, poppedOp.note!!.priority, poppedOp.note!!.gid, poppedOp.note!!.due, poppedOp.note!!.idx) }).await()
                val grpName = gidtogname.getOrDefault(poppedOp.note!!.gid, "Ungrouped")
                if (response.status != 1) {
                    println("There was an error editing that item: " + response.error)
                } else {
                    println(poppedOp.note!!.text)
                    editNote(grpName, poppedOp.note!!.gid, poppedOp.note!!.gid, poppedOp.note!!.id, poppedOp.note!!.id, poppedOp.note!!.text, poppedOp.note!!.priority, poppedOp.note!!.last_edit,poppedOp.note!!.due, poppedOp.note!!.idx)
                }
            }
        }else if(poppedOp.op_type == "Edit Group"){
            GlobalScope.launch(Dispatchers.IO) {
                val response = (async { HttpRequest.editGroup(poppedOp.grp!!.id, poppedOp.grp!!.name) }).await()

                if (response.status != 1) {
                    println("There was an error editing that item: " + response.error)
                } else {
                    val old_name = gidtogname.getOrDefault(poppedOp.grp!!.id, "Ungrouped")
                    editGroup(old_name, poppedOp.grp!!.name, poppedOp.grp!!.id)
                    undo_stack.pop()
                    NoteView.add(poppedOp.grp!!.name)
                }
            }
        }
    }


    fun switchStack(typeOp: String): String{
        if(typeOp == "Add Note"){
            return "Delete Note"
        }else if(typeOp == "Delete Note"){
            return "Add Note"
        }else if(typeOp == "Add Group"){
            return "Delete Group"
        }else if(typeOp == "Delete Group"){
            return "Add Group"
        }

        return typeOp
    }


    fun addEditNoteOp(poppedOp: Operation): Operation{
        var redo_note = Note(poppedOp.note!!.id, poppedOp.note!!.gid)
        val gname = gidtogname.getOrDefault(poppedOp.note!!.gid, "Ungrouped")
        var note_idx = -1

        for (i in 0.. gidMappings[gname]!!.notes.size-1){
            if(gidMappings[gname]!!.notes[i].id == poppedOp.note!!.id){
                note_idx = i
                break
            }
        }
        val cur_note = gidMappings[gname]!!.notes[note_idx]
        redo_note.text = cur_note.text
        redo_note.priority = cur_note.priority
        redo_note.last_edit = cur_note.last_edit
        redo_note.due = cur_note.due
        redo_note.idx = cur_note.idx
        var new_op = Operation("Edit Note", redo_note , null)
        return new_op
    }


    fun addEditGroupOp(poppedOp: Operation): Operation{
        var new_grp = Group(poppedOp.grp!!.id)
        new_grp.name = gidtogname.getOrDefault(poppedOp.grp!!.id, "Ungrouped")
        val new_op = Operation("Edit Group", null, new_grp)
        return new_op
    }

    fun popUndo(){
        if(undo_stack.size == 0){
            return
        }

        var poppedOp = undo_stack.pop()
        if(poppedOp.op_type == "Edit Note"){
            redo_stack.push(addEditNoteOp(poppedOp))
        }else if(poppedOp.op_type == "Edit Group"){
            redo_stack.push(addEditGroupOp(poppedOp))
        }

        doOperations(poppedOp, redo_stack)

    }


    fun popRedo(){
        if(redo_stack.size == 0){
            return
        }
        var poppedOp = redo_stack.pop()
        if(poppedOp.op_type == "Edit Note"){
            undo_stack.push(addEditNoteOp(poppedOp))
        }else if(poppedOp.op_type == "Edit Group"){
            undo_stack.push(addEditGroupOp(poppedOp))
        }

        doOperations(poppedOp, undo_stack)

    }


    fun addGroup(group_name: String, gid: Int){
        var new_group = Group(gid)
        new_group.name = group_name
        gidMappings.put(group_name, new_group)
        gidtogname.put(gid, group_name)
        sideBar.addGroup(gid, group_name)
        pushUndo("Add Group", null, new_group)
    }

    fun addNote(gname: String, gid: Int, note_id: Int, text: String, priority: Int, last_edit: String, due: String, idx: Int){
        var new_note = Note(note_id, gid)
        new_note.text = text
        new_note.priority = priority
        new_note.last_edit = last_edit
        new_note.due = due
        new_note.idx = idx
        gidMappings[gname]!!.notes.add(new_note)
        pushUndo("Add Note", new_note, null)
        broadcast()
    }

    fun deleteNote(gname: String, note_id: Int) {
        val check = Predicate { note: Note -> note.id == note_id }
        val deleted = gidMappings[gname]!!.notes.find { x: Note -> check.test(x) }
        pushUndo("Delete Note", deleted, null)
        gidMappings[gname]!!.notes.removeIf { x: Note -> check.test(x) }

        gidMappings[gname]!!.notes.forEach { x: Note ->
            if ( x.idx >= deleted!!.idx ) {
                x.idx--
                GlobalScope.launch(Dispatchers.IO) {
                    val response =
                        (async { HttpRequest.editTask(x.id, x.text, x.priority, x.gid, x.due, x.idx) }).await()
                    if (response.status != 1) {
                        println("There was an error editing that item: " + response.error)
                    }
                }
            }
        }
        broadcast()
    }

    fun editNote(gname: String, old_gid: Int, gid: Int, old_note_id: Int, note_id: Int, text: String, priority: Int, last_edit: String, due: String, idx: Int) {

        var note_idx = -1

        for (i in 0.. gidMappings[gname]!!.notes.size-1){
            if(gidMappings[gname]!!.notes[i].id == note_id){
                note_idx = i
                break
            }
        }
        if(old_gid != gid){
            deleteNote(gidtogname.getOrDefault(old_gid, "Ungrouped"), old_note_id)
            addNote(gname, gid, note_id, text, priority, last_edit, due, idx)
        }else{
            gidMappings[gname]!!.notes[note_idx].gid = gid
            gidMappings[gname]!!.notes[note_idx].text = text
            gidMappings[gname]!!.notes[note_idx].priority = priority
            gidMappings[gname]!!.notes[note_idx].last_edit = last_edit
            gidMappings[gname]!!.notes[note_idx].due = due
            gidMappings[gname]!!.notes[note_idx].idx = idx
            broadcast()
        }

    }


    fun swapNoteIndex(gname: String, note_id: Int, note_id2: Int, idx: Int, idx2: Int){
        gidMappings[gname]!!.notes[idx2].idx = idx
        gidMappings[gname]!!.notes[idx].idx = idx2
        val temp_note = gidMappings[gname]!!.notes[idx]
        gidMappings[gname]!!.notes[idx] =  gidMappings[gname]!!.notes[idx2]
        gidMappings[gname]!!.notes[idx2] = temp_note

        broadcast()
    }

    fun deleteGroup(gname: String) {
        gidMappings.remove(gname)
        NoteView.remove(gname)
    }

    fun editGroup(old_gname: String, new_gname: String, gid: Int) {
        val duplicateGroup = gidMappings[old_gname]
        val undo_grp = Group(gid)
        undo_grp.name = old_gname
        pushUndo("Edit Group", null, undo_grp)
        gidMappings.remove(old_gname)
        if (duplicateGroup != null) {
            gidMappings.put(new_gname, duplicateGroup)
        }
        gidtogname[gid] = new_gname
    }

    /**
     * Call this function to broadcast any changes to view/listeners
     */
    fun broadcast() {
        listeners.forEach { it?.invalidated(this)}
    }

    /**
     * List of groups (group class in Group.kt)
     */
    val groups = mutableListOf<Group>()

    init {
        populate()
        addGroup("Ungrouped", -1)
    }

    /**
     * function to read database and populate groups and notes
     */
    fun populate() {
        GlobalScope.launch(Dispatchers.IO) {
            val groups = (async { HttpRequest.getGroups() }).await()
            groups.forEachIndexed { i, item ->
                var new_group = Group(item.get("group_id")!!.toInt())
                new_group.name = item.get("group_name").orEmpty()
                gidMappings.put(item.get("group_name").orEmpty(), new_group)
                gidtogname.put(item.get("group_id")!!.toInt(), item.get("group_name").orEmpty())
            }
            for((gname, grp) in gidMappings){
                val notes = (async { HttpRequest.getTasksFromGroup(grp.id) }).await()
                notes.forEachIndexed { i, item ->
                    gidMappings[gname]!!.notes.add(Note(-1,-2))
                }

                notes.forEachIndexed { i, item ->
                    var new_note = Note(item.get("id")!!.toInt(), grp.id)
                    new_note.text = item.get("text").orEmpty()
                    new_note.priority = item.get("priority")!!.toInt()
                    new_note.last_edit = item.getOrDefault("last_edit", "No last edit")
                    new_note.due = item.getOrDefault("due", "no due date")
                    new_note.idx = item.get("idx")!!.toInt()
                    gidMappings[gname]!!.notes[new_note.idx] = new_note
                }
            }
            sideBar.createGroups(initial = true)
            broadcast()
        }

    }

}
