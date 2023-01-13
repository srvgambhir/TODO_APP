package todo.ui

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.stage.Stage
import todo.ui.view.*
import java.io.*
import java.util.*

class ToDoApplication: Application() {
    override fun start(stage: Stage) {
        val new_item = KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN)
        val delete_item = KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN)
        val edit_item = KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN)
        val copy_item = KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN)
        val cut_item = KeyCodeCombination(KeyCode.X, KeyCombination.SHORTCUT_DOWN)
        val paste_item = KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN)
        val move_up_item = KeyCodeCombination(KeyCode.UP, KeyCombination.SHORTCUT_DOWN)
        val move_down_item = KeyCodeCombination(KeyCode.DOWN, KeyCombination.SHORTCUT_DOWN)
        val undo = KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN)
        val redo = KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN)
        stage.title = "TrackIt"
        stage.scene = Scene(View())
        stage.scene.addEventHandler(KeyEvent.KEY_RELEASED) {
            if (new_item.match(it)) {
                toolBar.openModal()
            } else if (delete_item.match(it)) {
                if ( stage.scene.focusOwnerProperty().get() is NoteBox ) {
                    (stage.scene.focusOwnerProperty().get() as NoteBox).deleteNote()
                } else if ( stage.scene.focusOwnerProperty().get() is GroupBox ) {
                    (stage.scene.focusOwnerProperty().get() as GroupBox).deleteGroup()
                }
            } else if (edit_item.match(it)) {
                if ( stage.scene.focusOwnerProperty().get() is NoteBox) {
                    (stage.scene.focusOwnerProperty().get() as NoteBox).openModal()
                }
            } else if (copy_item.match(it)) {
                if ( stage.scene.focusOwnerProperty().get() is NoteBox) {
                    (stage.scene.focusOwnerProperty().get() as NoteBox).copyCutNote("copy")
                }
            } else if (cut_item.match(it)) {
                if ( stage.scene.focusOwnerProperty().get() is NoteBox) {
                    (stage.scene.focusOwnerProperty().get() as NoteBox).copyCutNote("cut")
                }
            } else if (paste_item.match(it)) {
                if ( stage.scene.focusOwnerProperty().get() is GroupOfNotes) {
                    (stage.scene.focusOwnerProperty().get() as GroupOfNotes).pasteNote()
                }
            } else if (move_up_item.match(it)) {
                if ( stage.scene.focusOwnerProperty().get() is NoteBox) {
                    (stage.scene.focusOwnerProperty().get() as NoteBox).moveUp()
                }
            } else if (move_down_item.match(it)) {
                if ( stage.scene.focusOwnerProperty().get() is NoteBox) {
                    (stage.scene.focusOwnerProperty().get() as NoteBox).moveDown()
                }
            } else if (undo.match(it)) {
                toolBar.undo()
            } else if (redo.match(it)) {
                toolBar.redo()
            }
        }
        stage.apply {
            minWidth = 800.0
            minHeight = 600.0
            try {
                val s = Scanner(File("dimensions.txt"))
                val line: String = s.nextLine()
                width =  line.toDouble()
                val line2: String = s.nextLine()
                height = line2.toDouble()

            } catch (ex: FileNotFoundException) {
                width = minWidth
                height = minHeight
            }
            setOnHiding { event ->
                try {
                    BufferedWriter(FileWriter("dimensions.txt")).use { bf ->
                        bf.write("${width}")
                        bf.newLine()
                        bf.write("${height}")
                    }
                } catch (ex: IOException) {
                    println("Problem saving")
                }
            }
        }
        stage.show()
    }
}

fun main(args: Array<String>) {
    Application.launch(ToDoApplication::class.java)
}