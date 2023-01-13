package todo.ui.view

import io.ktor.http.*
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.MouseButton
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.stage.Modality
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import todo.ui.model.Note
import todo.ui.*
import todo.dtos.ClipboardNote
import todo.ui.model.Model
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*


class View: BorderPane(), InvalidationListener {
    val side_bar = sideBar
    val tool_bar = toolBar

    init {
        Model.addListener(this)
        invalidated(null)
        left = side_bar.left
        bottom = tool_bar.bottom_box
        minWidth = 700.0
        center = ScrollPane(NoteView).apply {
            isFitToWidth = true
            isFitToHeight = true
        }
    }

    /**
     * Function that is called whenever Model broadcasts a change
     */
    override fun invalidated(observable: Observable?) {

    }
}

class GroupBox(val gid: Int, var name: String): HBox(), InvalidationListener {
    var old_name: String = ""
    val text = TextField().apply {
        background = Background(BackgroundFill(Color.LIGHTGREY, CornerRadii(0.0), Insets(0.0) ))
        text = name
        focusedProperty().addListener { observable, oldValue, newValue ->
            if (newValue) {
                old_name = text
            }
            if (!newValue) {
                if (Model.gidMappings.containsKey(text)) {
                    text = old_name
                }
                else {
                    GlobalScope.launch(Dispatchers.IO) {
                        val response =
                            (async { HttpRequest.editGroup(gid, text) }).await()

                        if (response.status != 1) {
                            println("There was an error editing that item: " + response.error)
                        } else {
                            name = text
                            Model.editGroup(old_name, name, gid)
                            if (NoteView.display_groups.contains(old_name)) {
                                NoteView.remove(old_name)
                                NoteView.show(name)
                            }
                        }
                    }
                }
            }
        }
    }
    val delete = Button("X").apply {
        setOnAction {
            deleteGroup()
        }
    }

    fun deleteGroup() {
        GlobalScope.launch(Dispatchers.IO) {
            checkBox.isSelected = false

            if (name != "Ungrouped") {
                Model.pushUndo("Delete Group", null, Model.gidMappings[name])
                val deleteNotes = mutableListOf<Note>()
                Model.gidMappings[name]!!.notes.forEach {
                    deleteNotes.add(it)
                }
                deleteNotes.forEach {
                    val response = (async { HttpRequest.deleteTask(it.id) }).await()
                    if (!response.status.isSuccess()) {
                        println("There was an error in deleting the note.")
                    }
                    //else {
                    //    Model.deleteNote(name, it.id)
                    //    Model.undo_stack.pop()
                    //}
                }
                val response = (async { HttpRequest.deleteGroup(gid) }).await()

                if (!response.status.isSuccess()) {
                    println("There was an error in deleting the note.")
                } else {
                    Model.deleteGroup(name)
                    sideBar.deleteGroup(this@GroupBox)
                }
            }
        }
    }

    val checkBox = CheckBox().apply {
        setOnAction {
            if (isSelected) {
                NoteView.show(name)
            }
            else {
                NoteView.remove(name)
            }
        }
    }
    init {
        spacing = 10.0
        children.add(checkBox)
        children.add(text)
        if (name != "Ungrouped") {
            children.add(delete)
        }

        Model.addListener(this)
        invalidated(null)
    }

    override fun invalidated(observable: Observable?) {
        if (checkBox.isSelected) {
            NoteView.show(name)
        }
    }
}

object sideBar {
    var groups_box = VBox().apply{
        spacing = 10.0
    }
    var left = ScrollPane(groups_box).apply {
        vbarPolicy = ScrollPane.ScrollBarPolicy.ALWAYS
        isFitToHeight = true
        padding = Insets(10.0, 10.0, 10.0, 10.0)
    }

    val grp_text = TextField("Group name")
    val create_btn = Button("+")
    val create_group = HBox(grp_text, create_btn).apply{
        spacing = 10.0
    }
    val label_grp = Label("Groups").apply {
        setAlignment(Pos.CENTER)
        font = Font("Arial", 18.0)
    }
    init{
        groups_box.children.add(label_grp)
        groups_box.children.add(create_group)
        create_btn.setOnAction(){
            if (Model.gidMappings.contains(grp_text.text)) {
                grp_text.text = "Group name"
            }
            else {
                GlobalScope.launch(Dispatchers.IO) {
                    val response =
                        (async { HttpRequest.addGroup(grp_text.getText()) }).await()

                    if (response.status != 1) {
                        println("There was an error editing that group: " + response.error)
                    } else {
                        println("Group added!\n")
                        var gid = response.message.toIntOrNull() ?: -1
                        Model.addGroup(grp_text.getText(), gid)
                    }
                    grp_text.text = "Group name"
                }
            }
        }
    }

    fun createGroups(initial: Boolean = false) {
        Platform.runLater {
            groups_box.children.clear()
            groups_box.children.add(label_grp)
            groups_box.children.add(create_group)
            Model.gidMappings.forEach {
                val newGroup = GroupBox(it.value.id, it.key)
                newGroup.backgroundProperty().bind(Bindings
                    .`when`(newGroup.focusedProperty())
                    .then( Background(BackgroundFill(Color.LIGHTSLATEGREY, CornerRadii(0.0), Insets(0.0))) )
                    .otherwise(Background(BackgroundFill(Color.TRANSPARENT, CornerRadii(0.0), Insets(0.0))))
                )
                newGroup.setOnMouseClicked {
                    newGroup.requestFocus()
                }
                if (initial && it.key == "Ungrouped") {
                    newGroup.checkBox.isSelected = true
                    NoteView.show("Ungrouped")
                }
                groups_box.children.add(newGroup)
            }
        }
    }
    fun addGroup(gid: Int, gname: String) {
        Platform.runLater {
            val newGroup = GroupBox(gid, gname)
            newGroup.setOnMouseClicked {
                newGroup.requestFocus()
            }
            groups_box.children.add(newGroup)
        }
    }

    fun deleteGroup(node: Node) {
        Platform.runLater {
            groups_box.children.remove(node)
        }
    }

    fun deleteLastGroup(){
        Platform.runLater {
            groups_box.children.removeAt(groups_box.children.size - 1)
        }
    }

}

object toolBar {
    val add_task = Button("Add task")
    val mode_btn = Button("Dark Mode")
    val rightAlign = Pane()
    val filter_text = Label("Filter By:")
    val filter_opts = listOf<String>("-", "Low", "Med", "High")
    val undo = Button("Undo")
    val redo = Button("Redo")
    val filter_list = FXCollections.observableList(filter_opts)
    val filter_menu = ChoiceBox(filter_list)
    val sort_text = Label("Sort By:")
    val sort_opts = listOf<String>("-", "Low-High", "High-Low")
    val sort_list = FXCollections.observableList(sort_opts)
    val sort_menu = ChoiceBox(sort_list)
    var bottom_box = HBox()
    var filter_by = 0
    var sort_by = ""
    var is_filtered = false
    var is_sorted = false
    var is_dark = true

    init {
        val tmpPane = Pane()
        HBox.setHgrow(tmpPane, Priority.ALWAYS)
        HBox.setHgrow(rightAlign, Priority.ALWAYS)

        bottom_box = HBox(add_task, tmpPane, mode_btn, filter_text, filter_menu, sort_text, sort_menu, undo, redo).apply{
            spacing = 10.0
            padding = Insets(10.0, 23.0, 10.0, 10.0)
            alignment = Pos.CENTER_LEFT
        }
        add_task.setOnAction(){
            openModal()
        }
        filter_menu.apply {
            value = "-"
            setOnAction {
                if (value == "Low") {
                    filter_by = 3
                    is_filtered = true
                    sort_menu.isDisable = true
                } else if (value == "Med") {
                    filter_by = 2
                    is_filtered = true
                    sort_menu.isDisable = true
                } else if (value == "High") {
                    filter_by = 1
                    is_filtered = true
                    sort_menu.isDisable = true
                } else if (value == "-") {
                    filter_by = 0
                    is_filtered = false
                    sort_menu.isDisable = false
                }
                NoteView.display()
            }
        }
        sort_menu.apply {
            value = "-"
            setOnAction {
                if (value == "Low-High") {
                    sort_by = "Low-High"
                    is_sorted = true
                    filter_menu.isDisable = true
                } else if (value == "High-Low") {
                    sort_by = "High-Low"
                    is_sorted = true
                    filter_menu.isDisable = true
                } else if (value == "-") {
                    sort_by = ""
                    is_sorted = false
                    filter_menu.isDisable = false
                }
                NoteView.display()
            }
        }

        mode_btn.apply {
            setOnAction {
                if (text == "Dark Mode") {
                    is_dark = false
                    text = "Light Mode"
                    NoteView.display()
                } else {
                    is_dark = true
                    text = "Dark Mode"
                    NoteView.display()
                }
            }
        }

        undo.setOnAction(){
            undo()
        }

        redo.setOnAction(){
            redo()
        }
    }

    fun undo() {
        Model.popUndo()
    }

    fun redo() {
        Model.popRedo()
    }

    fun openModal() = runBlocking<Unit>{
        val dialog = Stage()
        dialog.title = "Create a new note!"
        dialog.initModality(Modality.APPLICATION_MODAL)

        val text_note = TextField("New note")
        val text_group = ComboBox(FXCollections.observableArrayList("Ungrouped"))
        Model.gidMappings.forEach {
            if (it.key != "Ungrouped") {
                text_group.items.add(it.key)
            }
        }
        text_group.promptText = "Pick Group"
        val due_date = DatePicker()
        val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")


        val low_prio = CheckBox("Low")
        val med_prio = CheckBox("Med")
        val high_prio = CheckBox("High")
        low_prio.setOnAction{
            high_prio.setSelected(false)
            med_prio.setSelected(false)
        }
        med_prio.setOnAction{
            low_prio.setSelected(false)
            high_prio.setSelected(false)
        }
        high_prio.setOnAction{
            low_prio.setSelected(false)
            med_prio.setSelected(false)
        }

        val priority_box = HBox(low_prio, med_prio, high_prio).apply{
            spacing = 5.0
        }
        val create_note = Button("Create Note")
        create_note.setOnAction(){
            GlobalScope.launch(Dispatchers.IO) {
                var gid = -1
                var group_text = text_group.value
                if (Model.gidMappings.containsKey(group_text)) {
                    val id = Model.gidMappings[group_text]!!.id
                    gid = id
                }
                var priority = 1
                if (low_prio.isSelected) {
                    priority = 3
                } else if (med_prio.isSelected) {
                    priority = 2
                }
                var index = 0
                if(Model.gidMappings.containsKey(group_text)){
                    index =  Model.gidMappings[group_text]!!.notes.size
                }
                val response =
                    (async { HttpRequest.addTask(text_note.getText(), priority, gid, due_date.value.format(formatter), index) }).await()


                if (response.status != 1) {
                    println("There was an error editing that item: " + response.error)
                } else {
                    println("Item edited!\n")
                    var note_id = response.message.toIntOrNull() ?: -1
                    val c = Calendar.getInstance()
                    val year = c.get(Calendar.YEAR).toString()
                    val month = c.get(Calendar.MONTH).toString()
                    val day = c.get(Calendar.DAY_OF_MONTH).toString()
                    val last_edit = month + "/" + day + "/" + year
                    if (!Model.gidMappings.containsKey(group_text)) {
                        group_text = "Ungrouped"
                        println("Adding to no group because group you entered doesn't exist")
                    }
                    Model.addNote(group_text, gid, note_id, text_note.getText(), priority, last_edit, due_date.value.format(formatter), index)
                }

                text_note.text = "New note"
                high_prio.setSelected(false)
                med_prio.setSelected(false)
                low_prio.setSelected(false)
            }
        }

        val dialogVbox = VBox(text_note, text_group, due_date, priority_box, create_note).apply{
            spacing = 10.0
            padding = Insets(10.0)
        }
        val dialogScene = Scene(dialogVbox, 300.0, 200.0);

        dialog.setScene(dialogScene);
        dialog.show();
    }
}

class NoteBox(var gname: String, var gid: Int, var note_id: Int, var tex: String, var priority: Int, var last_edit: String, var due: String): VBox() {
    val textField = TextField().apply {
        background = Background(BackgroundFill(Color.PALEGOLDENROD, CornerRadii(0.0), Insets(0.0)))
        text = tex
        isEditable = false
    }
    val priority_label = Label("Priority: " + when(priority) {1 -> "High" 2 -> "Med" else -> "Low"}).apply {
        var color = Color.LIGHTBLUE
        if (priority == 1) {
            color = Color.LIGHTCORAL
        } else if (priority == 2) {
            color = Color.LIGHTSALMON
        }
        background = Background(BackgroundFill(color, CornerRadii(0.0), Insets(0.0)))
        padding = Insets(5.0)
    }
    val due_label = Label("Due: " + due).apply {
        background = Background(BackgroundFill(Color.LIGHTGREY, CornerRadii(0.0), Insets(0.0)))
        padding = Insets(5.0)
    }
    val delete_button = Button("X").apply {
        background = Background(BackgroundFill(Color.RED, CornerRadii(0.0), Insets(0.0)))
        border = Border(BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths(1.0)))
        padding = Insets(5.0,0.0,5.0,0.0)
        prefWidth = 30.0
    }
    val edit = Button("Edit").apply {
        padding = Insets(5.0)
        prefWidth = 60.0
    }

    val reorder_down = Button("Down").apply {
        background = Background(BackgroundFill(Color.LIGHTPINK, CornerRadii(0.0), Insets(0.0)))
        padding = Insets(5.0,0.0,5.0,0.0)
        prefWidth = 60.0
    }
    val reorder_up = Button("Up").apply {
        background = Background(BackgroundFill(Color.LIGHTGREEN, CornerRadii(0.0), Insets(0.0)))
        padding = Insets(5.0,0.0,5.0,0.0)
        prefWidth = 60.0
    }

    val copy = MenuItem("Copy")
    val cut = MenuItem("Cut")

    val tmpPane = Pane()
    init {
        children.add(HBox(textField, delete_button).apply {
            spacing = 10.0
        })
        HBox.setHgrow(textField, Priority.ALWAYS)
        children.add(HBox(priority_label, due_label, reorder_up, reorder_down, tmpPane, edit).apply {
            spacing = 10.0
        })
        HBox.setHgrow(tmpPane, Priority.ALWAYS)
        spacing = 10.0
        padding = Insets(10.0)
        isFillWidth = true
        border = Border(BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths(1.0)))

        delete_button.setOnAction() {
            deleteNote()
        }

        edit.setOnAction(){
            openModal()
        }

        copy.setOnAction() {
            copyCutNote("copy")
        }

        cut.setOnAction() {
            copyCutNote("cut")
        }

        reorder_up.setOnAction(){
            moveUp()
        }

        reorder_down.setOnAction(){
            moveDown()
        }
    }

    fun copyCutNote(type: String) {
        val clipboard = Clipboard.getSystemClipboard()
        val content = ClipboardContent()
        var note = ClipboardNote(type, note_id, gid, gname, tex, due, priority)

        content.putString(Json.encodeToString(note))
        clipboard.setContent(content)
    }

    fun getNoteIdx(gname: String, note_id: Int): Int{
        var note_idx = 0

        for (i in 0..Model.gidMappings[gname]!!.notes.size-1){
            if(Model.gidMappings[gname]!!.notes[i].id == note_id){
                note_idx = i
                break
            }
        }

        return note_idx
    }


    fun moveNote(newIdx: Int, prevIdx: Int){
        var note_need_swapping = Model.gidMappings[gname]!!.notes[newIdx]
        var note = Model.gidMappings[gname]!!.notes[prevIdx]

        GlobalScope.launch(Dispatchers.IO) {
            val response =
                (async { HttpRequest.editTask(note.id, note.text, note.priority, note.gid, note.due, newIdx) }).await()

            val response_note_swap =
                (async { HttpRequest.editTask(note_need_swapping.id, note_need_swapping.text, note_need_swapping.priority, note_need_swapping.gid, note_need_swapping.due, prevIdx) }).await()

            if (response.status != 1 && response_note_swap.status != -1) {
                println("There was an error editing that item: " + response.error)
            } else {
                println("Item edited!\n")
                var note_id = response.message.toIntOrNull() ?: -1
                var other_note_id = response_note_swap.message.toIntOrNull() ?: -1

                Model.swapNoteIndex(gname, note_id, other_note_id, newIdx, prevIdx)
            }
        }
    }


    fun moveDown(){

        val note_idx = getNoteIdx(gname, note_id)

        if(Model.gidMappings[gname]!!.notes.size-1 >= note_idx + 1){
            moveNote(note_idx + 1, note_idx)
        }

    }


    fun moveUp(){

        val note_idx = getNoteIdx(gname, note_id)

        var note = Model.gidMappings[gname]!!.notes[note_idx]
        if(0 <= note_idx - 1){
            moveNote(note_idx - 1, note_idx)
        }
    }


    fun openModal() = runBlocking<Unit>{

        var note_idx = getNoteIdx(gname, note_id)
        var note = Model.gidMappings[gname]!!.notes[note_idx]

        val dialog = Stage();
        dialog.title = "Edit note!"
        dialog.initModality(Modality.APPLICATION_MODAL);

        val text_note = TextField(note.text)
        val text_group = ComboBox(FXCollections.observableArrayList("Ungrouped"))
        Model.gidMappings.forEach {
            if (it.key != "Ungrouped") {
                text_group.items.add(it.key)
            }
        }
        text_group.promptText = "Pick Group"

        val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
        var date = LocalDate.parse(note.due, formatter)
        val due_date = DatePicker(date)


        val low_prio = CheckBox("Low")
        val med_prio = CheckBox("Med")
        val high_prio = CheckBox("High")
        if(note.priority == 1){
            low_prio.setSelected(true)
        }else if(note.priority == 2){
            med_prio.setSelected(true)
        }else{
            high_prio.setSelected(true)
        }

        low_prio.setOnAction{
            high_prio.setSelected(false)
            med_prio.setSelected(false)
        }
        med_prio.setOnAction{
            low_prio.setSelected(false)
            high_prio.setSelected(false)
        }
        high_prio.setOnAction{
            low_prio.setSelected(false)
            med_prio.setSelected(false)
        }

        val priority_box = HBox(low_prio, med_prio, high_prio).apply{
            spacing = 5.0
        }
        val edit_note = Button("Edit Note")
        edit_note.setOnAction(){
            GlobalScope.launch(Dispatchers.IO) {
                var gid = -1
                var group_text = gname
                try {
                    group_text = text_group.value
                }catch (e:Exception) {
                    println("no group selected so using previous")
                }
                    if (Model.gidMappings.containsKey(group_text)) {
                    val id = Model.gidMappings[group_text]!!.id
                    gid = id
                }
                var priority = 1
                if (low_prio.isSelected) {
                    priority = 3
                } else if (med_prio.isSelected) {
                    priority = 2
                }

                if(note.gid != gid){
                    note_idx = Model.gidMappings[group_text]!!.notes.size
                }else{
                    var undo_note = Note(note.id, note.gid)
                    undo_note.text = note.text
                    undo_note.priority = note.priority
                    undo_note.gid = note.gid
                    undo_note.last_edit = note.last_edit
                    undo_note.due = note.due
                    undo_note.idx = note.idx
                    Model.pushUndo("Edit Note", undo_note, null)
                }

                val response =
                    (async { HttpRequest.editTask(note.id, text_note.getText(), priority, gid, due_date.value.format(formatter), note_idx) }).await()

                if (response.status != 1) {
                    println("There was an error editing that item: " + response.error)
                } else {
                    println("Item edited!\n")
                    var note_id = response.message.toIntOrNull() ?: -1
                    val c = Calendar.getInstance()
                    val year = c.get(Calendar.YEAR).toString()
                    val month = c.get(Calendar.MONTH).toString()
                    val day = c.get(Calendar.DAY_OF_MONTH).toString()
                    val last_edit = month + "/" + day + "/" + year
                    if (!Model.gidMappings.containsKey(group_text)) {
                        group_text = "Ungrouped"
                        println("Adding to no group because group you entered doesn't exist")
                    }

                    Model.editNote(group_text, note.gid, gid, note.id, note_id, text_note.getText(), priority, last_edit, due_date.value.format(formatter), note.idx)
                }

            }
        }

        val dialogVbox = VBox(text_note, text_group, due_date, priority_box, edit_note).apply{
            spacing = 10.0
            padding = Insets(10.0)
        }
        val dialogScene = Scene(dialogVbox, 300.0, 200.0);

        dialog.setScene(dialogScene);
        dialog.show();
    }

    fun deleteNote() = runBlocking<Unit> {
        GlobalScope.launch(Dispatchers.IO) {
            val response = (async { HttpRequest.deleteTask(note_id) }).await()

            if ( !response.status.isSuccess()) {
                println("There was an error in deleting the note.")
            } else {
                Model.deleteNote(gname, note_id)
            }
        }
    }
}

class GroupOfNotes(gname: String): VBox() {
    var gname = gname
    val paste = MenuItem("Paste")

    init {
        isFillWidth = true

        run breaking@{
            if (Model.gidMappings[gname]!!.notes.size == 0) {
                children.add(Label("This group is currently empty").apply {
                    font = Font.font(15.0)
                    alignment = Pos.TOP_CENTER
                    maxWidth = Double.MAX_VALUE
                    padding = Insets(10.0)
                    background = Background(BackgroundFill(Color.TRANSPARENT, CornerRadii(0.0), Insets(0.0)))
                })
            } else {
                Model.gidMappings[gname]!!.notes.forEach {
                    if (toolBar.is_filtered) {
                        if (it.priority == toolBar.filter_by) {
                            NoteView.addChild(gname, it)
                        }
                    } else if (toolBar.is_sorted) {
                        if (toolBar.sort_by == "Low-High") {
                            NoteView.displaySorted(gname, toolBar.sort_by)
                        } else if (toolBar.sort_by == "High-Low") {
                            NoteView.displaySorted(gname, toolBar.sort_by)
                        }
                        return@breaking
                    } else {
                        NoteView.addChild(gname, it)
                    }
                }
            }
        }

        paste.setOnAction() {
            pasteNote()
        }
    }

    fun pasteNote() = runBlocking<Unit> {
        val clipboard = Clipboard.getSystemClipboard()
        if (clipboard.hasString()) {
            try {
                val old_note = Json.decodeFromString<ClipboardNote>(clipboard.string)

                var new_gid = -1
                if (Model.gidMappings.containsKey(gname)) {
                    val id = Model.gidMappings[gname]!!.id
                    new_gid = id
                }

                var index = 0
                if (Model.gidMappings.containsKey(gname)) {
                    index = Model.gidMappings[gname]!!.notes.size
                }

                GlobalScope.launch(Dispatchers.IO) {
                    if (old_note.type == "cut") {
                        val response = (async { HttpRequest.deleteTask(old_note.id) }).await()

                        if (!response.status.isSuccess()) {
                            println("There was an error in deleting the old note.")
                        } else {
                            Model.deleteNote(old_note.gname, old_note.id)
                        }
                    }

                    val response =
                        (async {
                            HttpRequest.addTask(
                                old_note.text,
                                old_note.priority,
                                new_gid,
                                old_note.due,
                                index
                            )
                        }).await()

                    if (response.status != 1) {
                        println("There was an error pasting that item: " + response.error)
                    } else {
                        println("Item pasted!\n")
                        var note_id = response.message.toIntOrNull() ?: -1
                        val c = Calendar.getInstance()
                        val year = c.get(Calendar.YEAR).toString()
                        val month = c.get(Calendar.MONTH).toString()
                        val day = c.get(Calendar.DAY_OF_MONTH).toString()
                        val last_edit = month + "/" + day + "/" + year
                        Model.addNote(
                            gname,
                            new_gid,
                            note_id,
                            old_note.text,
                            old_note.priority,
                            last_edit,
                            old_note.due,
                            index
                        )
                    }
                }
            } catch (e: IllegalArgumentException) { }
        }
    }
}

object NoteView: VBox() {
    var display_groups = mutableSetOf<String>()

    init {
        isFillWidth = true
        spacing = 10.0
        padding = Insets(10.0)
    }

    fun show(gname: String) {
        Platform.runLater {
            display_groups.add(gname)
            display()
        }
        /**
         * For sorting, we can simply sort the children's list by index
         */
    }

    fun remove(gname: String) {
        if (display_groups.contains(gname)) {
            display_groups.remove(gname)
        }
        display()
    }


    fun add(new_gname: String){
        display_groups.add(new_gname)
    }

    fun changeBackground(mode : String) {
        var color = Color.LIGHTSLATEGREY
        if (mode == "dark") {
            color = Color.DARKGRAY
            NoteView.background = Background(BackgroundFill(color, CornerRadii(0.0), Insets(0.0)))
            sideBar.groups_box.background = Background(BackgroundFill(color, CornerRadii(0.0), Insets(0.0)))
            sideBar.left.background = Background(BackgroundFill(color, CornerRadii(0.0), Insets(0.0)))
            sideBar.create_group.background = Background(BackgroundFill(color, CornerRadii(0.0), Insets(0.0)))
            toolBar.bottom_box.background = Background(BackgroundFill(color, CornerRadii(0.0), Insets(0.0)))
            toolBar.bottom_box.border = Border(BorderStroke(Color.WHITE, BorderStrokeStyle.SOLID,CornerRadii.EMPTY, BorderWidths(2.0)))
        } else {
            NoteView.background = Background(BackgroundFill(color, CornerRadii(0.0), Insets(0.0)))
            sideBar.groups_box.background = Background(BackgroundFill(color, CornerRadii(0.0), Insets(0.0)))
            sideBar.left.background = Background(BackgroundFill(color, CornerRadii(0.0), Insets(0.0)))
            sideBar.create_group.background = Background(BackgroundFill(color, CornerRadii(0.0), Insets(0.0)))
            toolBar.bottom_box.background = Background(BackgroundFill(color, CornerRadii(0.0), Insets(0.0)))
            toolBar.bottom_box.border = Border(BorderStroke(Color.WHITE, BorderStrokeStyle.SOLID,CornerRadii.EMPTY, BorderWidths(2.0)))
        }
    }

    fun addChild(name : String, note : Note) {
        val nb = NoteBox(name, note.gid, note.id, note.text, note.priority, note.last_edit, note.due)
        nb.backgroundProperty().bind(Bindings
            .`when`(nb.focusedProperty())
            .then(Background(BackgroundFill(Color.LIGHTSLATEGREY, CornerRadii(0.0), Insets(0.0))))
            .otherwise(Background(BackgroundFill(Color.TRANSPARENT, CornerRadii(0.0), Insets(0.0))))
        )

        val menu = ContextMenu()
        menu.items.addAll(nb.copy, nb.cut)
        nb.setOnMouseClicked {
            if (it.button == MouseButton.SECONDARY) {
                menu.show(nb, it.screenX, it.screenY)
            } else {
                menu.hide()
                nb.requestFocus()
            }
            it.consume()
        }
        children.add(nb.apply {
            alignment = Pos.TOP_CENTER
        })
    }

    fun displaySorted(name : String, sort_opt : String) {
        if (sort_opt == "Low-High") {
            var curr_prior = 3
            Model.gidMappings[name]!!.notes.forEach {
                if (it.priority == curr_prior) {
                    addChild(name, it)
                }
            }
            curr_prior = 2
            Model.gidMappings[name]!!.notes.forEach {
                if (it.priority == curr_prior) {
                    addChild(name, it)
                }
            }
            curr_prior = 1
            Model.gidMappings[name]!!.notes.forEach {
                if (it.priority == curr_prior) {
                    addChild(name, it)
                }
            }
        } else if (sort_opt == "High-Low") {
            var curr_prior = 1
            Model.gidMappings[name]!!.notes.forEach {
                if (it.priority == curr_prior) {
                    addChild(name, it)
                }
            }
            curr_prior = 2
            Model.gidMappings[name]!!.notes.forEach {
                if (it.priority == curr_prior) {
                    addChild(name, it)
                }
            }
            curr_prior = 3
            Model.gidMappings[name]!!.notes.forEach {
                if (it.priority == curr_prior) {
                    addChild(name, it)
                }
            }
        }
    }

    fun display() {
        if (toolBar.is_dark) {
            changeBackground("dark")
        } else {
            changeBackground("light")
        }
        Platform.runLater {
            val removeList = mutableListOf<String>()
            children.clear()
            display_groups.forEach {
                val name = it
                if (Model.gidMappings.contains(it)) {
                    children.add(Label(name).apply {
                        font = Font.font(15.0)
                        alignment = Pos.TOP_CENTER
                        maxWidth = Double.MAX_VALUE
                        background = Background(BackgroundFill(Color.BLACK, CornerRadii(0.0), Insets(0.0)))
                        padding = Insets(5.0)
                        textFill = Color.WHITE
                    })

                    val group = GroupOfNotes(it)
                    group.padding = Insets(10.0)
                    val menu = ContextMenu()
                    menu.items.add(group.paste)
                    group.setOnMouseClicked {
                        if (it.button == MouseButton.SECONDARY) {
                            menu.show(group, it.screenX, it.screenY)
                        } else {
                            menu.hide()
                            group.requestFocus()
                        }
                        it.consume()
                    }
                    children.add(group)
                } else {
                    removeList.add(it)
                }
            }
            removeList.forEach {
                display_groups.remove(it)
            }
        }
    }
}