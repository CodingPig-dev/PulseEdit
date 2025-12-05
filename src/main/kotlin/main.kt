import java.awt.Desktop
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Files
import javax.swing.*
private const val MARKER = "\n--ED3-JSON-START--\n"

fun main() {
    SwingUtilities.invokeLater {
        val options = arrayOf("Create .ed3 from MP3", "Open .ed3 file", "Exit")
        while (true) {
            val choice = JOptionPane.showOptionDialog(
                null,
                "Choose an action",
                "ED3 Tool",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
            )
            when (choice) {
                0 -> createEd3Flow()
                1 -> openEd3Flow()
                else -> break
            }
        }
        System.exit(0)
    }
}

private fun createEd3Flow() {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Select MP3 file"
    chooser.fileSelectionMode = JFileChooser.FILES_ONLY
    val res = chooser.showOpenDialog(null)
    if (res != JFileChooser.APPROVE_OPTION) return
    val mp3File = chooser.selectedFile
    if (!mp3File.exists()) {
        JOptionPane.showMessageDialog(null, "Selected file does not exist.")
        return
    }

    val textArea = JTextArea(15, 60)
    textArea.lineWrap = true
    val scroll = JScrollPane(textArea)
    scroll.preferredSize = Dimension(600, 300)
    val confirm = JOptionPane.showConfirmDialog(null, scroll, "Paste JSON to embed", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
    if (confirm != JOptionPane.OK_OPTION) return
    val json = textArea.text.trim()
    if (json.isEmpty()) {
        JOptionPane.showMessageDialog(null, "No JSON provided.")
        return
    }
    if (!looksLikeJson(json)) {
        val ok = JOptionPane.showConfirmDialog(null, "The text does not look like JSON. Continue?", "Validation", JOptionPane.YES_NO_OPTION)
        if (ok != JOptionPane.YES_OPTION) return
    }

    val outFile = File(mp3File.parentFile, mp3File.name + ".ed3")
    try {
        val mp3Bytes = Files.readAllBytes(mp3File.toPath())
        val markerBytes = MARKER.toByteArray(Charsets.UTF_8)
        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val outBytes = ByteArray(mp3Bytes.size + markerBytes.size + jsonBytes.size)
        System.arraycopy(mp3Bytes, 0, outBytes, 0, mp3Bytes.size)
        System.arraycopy(markerBytes, 0, outBytes, mp3Bytes.size, markerBytes.size)
        System.arraycopy(jsonBytes, 0, outBytes, mp3Bytes.size + markerBytes.size, jsonBytes.size)
        Files.write(outFile.toPath(), outBytes)
        JOptionPane.showMessageDialog(null, "Created: ${outFile.absolutePath}")
    } catch (e: Exception) {
        e.printStackTrace()
        JOptionPane.showMessageDialog(null, "Error creating .ed3: ${e.message}")
    }
}

private fun openEd3Flow() {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Select .ed3 file"
    chooser.fileSelectionMode = JFileChooser.FILES_ONLY
    val res = chooser.showOpenDialog(null)
    if (res != JFileChooser.APPROVE_OPTION) return
    val ed3File = chooser.selectedFile
    if (!ed3File.exists()) {
        JOptionPane.showMessageDialog(null, "Selected file does not exist.")
        return
    }
    try {
        val allBytes = Files.readAllBytes(ed3File.toPath())
        val markerBytes = MARKER.toByteArray(Charsets.UTF_8)
        val idx = indexOfSequence(allBytes, markerBytes)
        val mp3Bytes: ByteArray
        val jsonString: String?
        if (idx >= 0) {
            mp3Bytes = allBytes.copyOfRange(0, idx)
            val jsonBytes = allBytes.copyOfRange(idx + markerBytes.size, allBytes.size)
            jsonString = String(jsonBytes, Charsets.UTF_8)
        } else {
            mp3Bytes = allBytes
            jsonString = null
        }
        val tempMp3 = File.createTempFile("ed3_temp_", ".mp3")
        tempMp3.deleteOnExit()
        Files.write(tempMp3.toPath(), mp3Bytes)
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(tempMp3)
        } else {
            JOptionPane.showMessageDialog(null, "Cannot open media player on this platform.")
        }
        if (jsonString != null) {
            showJsonDialog(jsonString)
        } else {
            JOptionPane.showMessageDialog(null, "No embedded JSON found in this .ed3 file.")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        JOptionPane.showMessageDialog(null, "Error opening .ed3: ${e.message}")
    }
}

private fun showJsonDialog(json: String) {
    val textArea = JTextArea(15, 60)
    textArea.isEditable = false
    textArea.text = json
    textArea.caretPosition = 0
    val scroll = JScrollPane(textArea)
    scroll.preferredSize = Dimension(600, 300)
    val copyButton = JButton("Copy to Clipboard")
    copyButton.addActionListener {
        val sel = StringSelection(json)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
        JOptionPane.showMessageDialog(null, "JSON copied to clipboard.")
    }
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.add(scroll)
    panel.add(copyButton)
    JOptionPane.showMessageDialog(null, panel, "Embedded JSON", JOptionPane.PLAIN_MESSAGE)
}
private fun looksLikeJson(s: String): Boolean {
    val trimmed = s.trim()
    return (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))
}
private fun indexOfSequence(data: ByteArray, pattern: ByteArray): Int {
    if (pattern.isEmpty()) return 0
    outer@ for (i in 0..data.size - pattern.size) {
        for (j in pattern.indices) {
            if (data[i + j] != pattern[j]) continue@outer
        }
        return i
    }
    return -1
}