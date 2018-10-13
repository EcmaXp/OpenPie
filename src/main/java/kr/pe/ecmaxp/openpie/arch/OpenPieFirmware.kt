package kr.pe.ecmaxp.openpie.arch

import kr.pe.ecmaxp.openpie.OpenPie
import kr.pe.ecmaxp.openpie.arch.types.Entry
import kr.pe.ecmaxp.thumbsf.CPU
import java.net.URL
import java.util.*

class OpenPieFirmware(val name: String = "debug") {
    val protocol = 1
    private val path: String = "/assets/${OpenPie.MODID}/firmwares/$name"

    companion object {
        val DEBUG = OpenPieFirmware()
    }

    init {
        if (name.indexOf('/') >= 0)
            throw Exception("Invalid Filename")

        getResource("firmware.bin")
    }

    private fun getResource(filename: String): URL {
        return OpenPieFirmware::class.java.getResource("$path/$filename")!!
    }

    fun loadFirmware(): ByteArray {
        val firmware = getResource("firmware.bin")
        return firmware.readBytes()
    }

    fun loadMapping(): List<Entry> {
        val file = getResource("firmware.map")
        val lines = file.readText().lines()
        val result = ArrayList<Entry>()

        fun parseHex(s: String): Int {
            if (!s.startsWith("0x"))
                throw Exception("Invalid Map File (0x)")

            return s.substring(2).toInt(16)
        }

        for (line in lines) {
            if (line.isEmpty())
                continue;

            val tokens = line.split('\t')
            if (tokens.size != 4)
                throw Exception("Invalid Map File")

            val address = parseHex(tokens[0])
            val size = parseHex(tokens[1])
            val type = tokens[2]
            val name = tokens[3]
            val target = Entry(address, size, type, name)
            result.add(target)
        }

        return result
    }

    fun findTarget(address: Int): Entry? {
        val mapping = loadMapping()
        var selected: Entry? = null
        for (target in mapping) {
            if (target.address <= address && address < target.address + target.size) {
                selected = target
            }
        }

        return selected
    }

    fun printLastTracebackCPU(cpu: CPU) {
        var selected = findTarget(cpu.regs.pc)
        if (selected != null) {
            println("last pc function :${selected.name} (+${cpu.regs.pc - selected.address})")
        } else {
            println("last pc function : (null) (${cpu.regs.pc})")
        }

        selected = findTarget(cpu.regs.lr)
        if (selected != null) {
            println("last lr function :${selected.name} (+${cpu.regs.lr - selected.address})")
        } else {
            println("last lr function : (null) (${cpu.regs.lr})")
        }
    }
}