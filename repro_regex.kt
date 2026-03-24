
fun main() {
    val line = "/tmp/linter_.v:32: syntax error"
    // Original regex from IcarusVerilogLinter.kt
    val regex = Regex("""^(.+?):(\d+)(?::\d+)?:\s*(error|warning):\s*(.+)$""", RegexOption.IGNORE_CASE)
    val match = regex.find(line)
    
    if (match != null) {
        println("Match found!")
        val (filePath, lineNum, severityStr, message) = match.destructured
        println("File: $filePath")
        println("Line: $lineNum")
        println("Severity: $severityStr")
        println("Message: $message")
    } else {
        println("No match found for line: $line")
    }

    // Testing a more flexible regex
    // Some iverilog outputs don't have : error: or : warning:
    // They may just have : syntax error or something else.
    // Let's try to match file:line: [optional_severity:] message
    val flexibleRegex = Regex("""^(.+?):(\d+)(?::\d+)?:\s*(?:(error|warning):\s*)?(.+)$""", RegexOption.IGNORE_CASE)
    val match2 = flexibleRegex.find(line)
    if (match2 != null) {
        println("\nFlexible match found!")
        val filePath = match2.groupValues[1]
        val lineNum = match2.groupValues[2]
        val severityStr = match2.groupValues[3] ?: "error" // Default to error if not specified, since syntax error is an error
        val message = match2.groupValues[4]
        println("File: $filePath")
        println("Line: $lineNum")
        println("Severity: $severityStr")
        println("Message: $message")
    } else {
        println("\nNo flexible match found for line: $line")
    }
}
