import java.io.File

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        val fileName = parseCommandLine(args)
        if (fileName == null) {
            println("You must enter a file name!")
            return;
        }
        println("file name is: $fileName")
        val bytes: ByteArray = loadGameFile(fileName)
        readHeader(bytes) // should put into a structure
        parseGameCommands(bytes)
    }
}

private fun parseCommandLine(args: Array<String>) : String? {
    if (args.size != 1) {
        println("Usage: d2 game_file>")
        println("Example: d2 zork1.z3")
        return null
    }
    return args[0]
}

/**
 * Read unsigned byte
 */
private fun readByte(bytes: ByteArray, offset: Int) : UByte {
    val byte:Byte = bytes[offset] // in Kotlin, a Byte is signed
    return  byte.toUByte() // ().toInt() and 255 // convert to unsigned
}

/**
 * Read unsigned word
 */
private fun readWord(bytes: ByteArray, offset: Int) : UInt {
    val byte1 = readByte(bytes, offset)
    val byte2 = readByte(bytes, offset + 1)
    // println("val = $byte1, $byte2")
    return byte1 * 256u + byte2
}

private fun readSerial(bytes: ByteArray, offset: Int) : String {
    var serial : String = ""
    for (i in 0..5) {
        serial += bytes[offset + i].toChar()
    }
    return serial
}

/*
http://frobnitz.co.uk/zmachine/infocom/spec-zip.pdf
ZVERSION
ZORKID
ENDLOD
START
VOCAB
OBJECT
GLOBALS
PURBOT
FLAGS
SERIAL
FWORDS
PLENTH
PCHKSM
(17 reserved words)
version of Z-machine used
unique game identifier
beginning of non-preloaded code
location where execution begins
points to vocabulary table
points to object table
points to global variable table
beginning of pure code
16 user-settable flags
serial number - 6 bytes
points to fwords table
length of program (in words)
checksum of all bytes
 */
private fun readHeader( bytes: ByteArray) {

    // Read header
    val SIZE = 17 * 2 // 17 reserved words in header
    if (bytes.size < SIZE)
        return; // error
    var byte = readByte(bytes, 0)
    println("ZVERSION (Z-machine version) = $byte")

    byte = readByte(bytes, 1)
    println("mode byte = $byte")

    var word = readWord(bytes, 2)
    println("ZORKID (version) = $word")

    word = readWord(bytes, 4)
    println("ENDLOD = $word, $" + word.toString(16))

    word = readWord(bytes, 6)
    println("START (execution) = $word, $" + word.toString(16))

    println("Value at start word: $" + (bytes[word.toInt()].toInt() and 255).toString(16) + " $" + (bytes[word.toInt() + 1].toInt() and 255).toString(16))

    word = readWord(bytes, 8)
    println("VOCAB = $word, $" + word.toString(16))

    word = readWord(bytes, 10)
    println("OBJECT = $word, $" + word.toString(16))

    word = readWord(bytes, 12)
    println("GLOBALS = $word, $" + word.toString(16))

    word = readWord(bytes, 14)
    println("PURBOT = $word, $" + word.toString(16))

    word = readWord(bytes, 16)
    println("FLAGS = $word, $" + word.toString(16))

    var serial : String = readSerial(bytes, 18)
    println("SERIAL = $serial")

    word = readWord(bytes, 24)
    println("FWORDS = $word, $" + word.toString(16))

    word = readWord(bytes, 26)
    word *= 2u // convert from words to bytes
    println("PLENTH (file length) in bytes = $word, $" + word.toString(16))

    word = readWord(bytes, 28)
    println("PCHKSM = $word, $" + word.toString(16))

    // print out code
    word = readWord(bytes, 6)


    println("Value at start word: $" + (bytes[word.toInt()].toUByte()).toString(16) + " $" + (bytes[word.toInt() + 1].toUByte()).toString(16))

}

/**
 * Load game file into memory
 */
private fun loadGameFile(fileName: String): ByteArray {
    val file = File(fileName)
    val bytes: ByteArray = file.readBytes()
    println("Size of file: " + bytes.size)
    return bytes
}

 private fun parseGameCommands( bytes: ByteArray) {
     var programCounter = readWord(bytes, 6)
     // println("START (execution) = $word, $" + word.toString(16))
     //println("Value at start word: $" + (bytes[word.toInt()].toUByte()).toString(16) + " $" + (bytes[word.toInt() + 1].toUByte()).toString(16))


     // See ZIP standard for this breakdown: 2OP, 1OP, etc.
     for (i in 1..10) {
         println("PC is " + programCounter.toString(16))
         val byte = readByte(bytes, programCounter.toInt()) // maybe should just use ints for everything...
         programCounter =
                 when {
                     byte < 128u -> parse2OP(byte, bytes, programCounter)
                     byte < 176u -> parse1OP(byte, programCounter)
                     byte < 192u -> parse0OP(byte, programCounter)
                     else -> parseEXT(byte, bytes, programCounter)
                 }
     }
 }

private fun parse0OP(byte: UByte, programCounter: UInt): UInt {
    println("parse0OP, value is $byte")
    check(false) {
        "@@@ Invalid parse0OP opcode $byte at $programCounter "
    }
    return programCounter
}

enum class OPERAND {
    LONG_IMMEDIATE, // 16 bits
    IMMEDIATE, // 8 bits
    VARIABLE // 0 - pop from stack, 1..15 local, 16..255 global
}

private fun parse1OP(byte: UByte, programCounter: UInt): UInt {
    println("parse1OP, value is $byte, 4 bits: " + (byte and 0xFu))
    /*
    Bits 5 & 4

00 long immediate
01 immediate
10 variable
11 undefined
     */
    check(false) {
        "@@@ Invalid 1OP opcode $byte at $programCounter "
    }
    return programCounter
}

private fun getVariableAddressModeDescription( opType : OPERAND , opcode : Int) =
    when (opcode) {
        0 -> "pop from stack"
        in 1..15 -> "local var " + (opcode - 1).toString(16)
        else -> "global " + (opcode - 16).toString(16)
    }

/**
 * 2 operand opcode
 */
private fun parse2OP(byte: UByte, bytes: ByteArray, programCounter: UInt): UInt {
    println("parse2OP, value is $byte, 5 bits: " + (byte and 0x1Fu))

    // for 2OP opcodes, get bits 6 & 5 to get addressing modes
    val firstOp = if (((byte.toInt() ushr 6) and 1) == 0) OPERAND.IMMEDIATE else OPERAND.VARIABLE
    val secondOp = if (((byte.toInt() ushr 5) and 1) == 0) OPERAND.IMMEDIATE else OPERAND.VARIABLE


    /*
    Bits 6 and 5 refer to the first and second operands, respectively. A zero specifies an immediate operand while a one specifies a variable operand:
00 immediate, immediate
01 immediate, variable
10 variable, immediate
11 variable, variable
     */
    var returnPC = programCounter + 1u
    var args = ArrayList<UInt>()
    when (byte.toUInt()) {
        84u -> { // ADD
            var arg = readByte(bytes, returnPC.toInt())
            args.add(arg.toUInt())
            returnPC += 1u
            arg = readByte(bytes, returnPC.toInt())
            args.add(arg.toUInt())
            returnPC += 1u

            println("ADD: " + getVariableAddressModeDescription(firstOp, args[0].toInt()) +
                    getVariableAddressModeDescription(secondOp, args[0].toInt()))
            val storeLocation = readByte(bytes, returnPC.toInt())
            returnPC += 1u
            when (storeLocation.toUInt()) {
                0u -> {
                    println("push the value onto the stack")
                }
                in 1u..15u   -> {
                    println("set local variable #1-15: $storeLocation")
                }
                else -> {
                    println("set global variable #16-255: $storeLocation")
                }
            }
        }
        else ->
            check(false) {
                "@@@ Invalid 2OP opcode $byte at $programCounter "
            }
    }
    return returnPC
}

/**
 * extended opcode has 0-4 operands
 */
private fun parseEXT(byte: UByte, bytes: ByteArray, programCounter: UInt): UInt {
    var returnPC = programCounter + 1u
    val extFormatModeByte = readByte(bytes, returnPC.toInt())
    println("parseEXT, value is $byte, extended format opcode mode byte is $extFormatModeByte")
    returnPC++

    var argCount = 0
    var args = ArrayList<UInt>()
    for (i in 6 downTo 0 step 2) {
        val twoBits = (extFormatModeByte.toInt() ushr i) and 3
        check(twoBits in 0..3)  {
            "  Extended Format Opocde mode byte two bit value must be < 4, is $twoBits"
        }
        when (twoBits.toUInt()) {
            0u -> {
                println("  ext format opcode arg: long immediate, 16 bits")
                val arg = readWord(bytes, returnPC.toInt())
                args.add(arg)
                returnPC += 2u
            }
            1u -> {
                println("  ext format opcode arg: immediate, 8 bits")
                val arg = readByte(bytes, returnPC.toInt())
                args.add(arg.toUInt())
                returnPC += 1u
            }
            2u -> {
                print("  ext format opcode arg: variable: ")
                val arg = readByte(bytes, returnPC.toInt())
                args.add(arg.toUInt())
                returnPC += 1u
                when (arg.toUInt()) {
                    0u -> {
                        println("  use the current top-of-stack slot, no push or pop")
                    }
                    in 1u..15u   -> {
                        println("  use local variable: $arg")
                    }
                    else -> {
                        println("  use global variable: $arg")
                    }
                }
            }
            3u -> break
        }
        argCount++
    }
    println("  args: $args")

    when (byte.toUInt()) {
        224u -> { // CALL
            // for Z3 version, up to 3 local variables set by CALL
            val functionPtr = 2u * args.removeFirst() // fcn is word pointer, convert to bytes
            check(functionPtr != 0u) {
                "@@@ Deal with calling function at address 0 - special, just returns RFALSE"
            }
            println("  CALL command to $functionPtr, $" +  functionPtr.toString(16))
            val numLocalVariables = readByte(bytes, functionPtr.toInt()) // go to function location to get # local variables
            println("  $numLocalVariables local variables")
            check(numLocalVariables.toInt() in 0..15)  {
                "  Number of local variables must be <= 15, is $numLocalVariables"
            }
            for (i in 0 until minOf(args.size, numLocalVariables.toInt())) {
                println("    arg: " + args[i] + ", $" +  args[i].toString(16))
            }
            val storeLocation = readByte(bytes, returnPC.toInt())
            returnPC += 1u
            when (storeLocation.toUInt()) {
                0u -> {
                    println("  push the value onto the stack")
                }
                in 1u..15u   -> {
                    println("  set local variable #1-15: $storeLocation")
                }
                else -> {
                    println("  set global variable #16-255: $storeLocation")
                }
            }
        }
        225u -> { // STOREW
            println("  STOREW command in array, index " + args[1] + " to value " + args[2])
        }
        227u -> { // PUT_PROP
            println("  PUT_PROP object " + args[0] + " property " + args[1] + " to value " + args[2])
        }
        else -> check(false) {
            "@@@ Invalid EXT opcode $byte at $programCounter "
        }

    }

    return returnPC
}
