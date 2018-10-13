package kr.pe.ecmaxp.openpie.arch

import kr.pe.ecmaxp.openpie.arch.consts.*
import kr.pe.ecmaxp.openpie.arch.state.FileHandle
import kr.pe.ecmaxp.openpie.arch.types.components.ComponentInvoke
import kr.pe.ecmaxp.openpie.arch.types.Interrupt
import kr.pe.ecmaxp.openpie.arch.types.value.ValueApply
import kr.pe.ecmaxp.openpie.arch.types.value.ValueCall
import kr.pe.ecmaxp.openpie.arch.types.value.ValueUnapply
import kr.pe.ecmaxp.thumbsf.consts.R0
import kr.pe.ecmaxp.thumbsf.signal.ControlPauseSignal
import kr.pe.ecmaxp.thumbsf.signal.ControlStopSignal
import li.cil.oc.api.machine.*
import li.cil.oc.api.network.Component
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets

class OpenPieInterruptHandler(val vm: OpenPieVirtualMachine) {
    operator fun invoke(intr: Interrupt, synchronized: Boolean) {
        try {
            val code: Int = when (intr.imm and (0xFFFF shl 16)) {
                SYS_CONTROL -> handleControl(intr, synchronized)
                SYS_DEBUG -> handleDebug(intr, synchronized)
                SYS_SIGNAL -> handleSignal(intr, synchronized)
                SYS_COMPONENTS -> handleComponents(intr, synchronized)
                SYS_VALUE -> handleValue(intr, synchronized)
                SYS_COMPUTER -> handleComputer(intr, synchronized)
                SYS_INFO -> handleInfo(intr, synchronized)
                SYS_TIMER -> handleTimer(intr, synchronized)
                SYS_VFS -> handleVirtualFileSystem(intr, synchronized)
                else -> throw UnknownInterrupt()
            }

            intr.cpu.regs[0] = code
        } catch (e: UnknownInterrupt) {
            e.printStackTrace()
            throw ControlStopSignal(ExecutionResult.Error("Unknown Interrupt?"))
        } catch (e: LimitReachedException) {
            throw ControlPauseSignal(ExecutionResult.SynchronizedCall())
        }
    }

    companion object {
        object SystemControlReturn
    }

    inner class UnknownInterrupt : Exception()

    val machine: Machine get() = vm.machine
    val state: OpenPieVirtualMachineState get() = vm.state

    private fun handleControl(intr: Interrupt, @Suppress("UNUSED_PARAMETER") synchronized: Boolean): Int {
        return when (intr.imm) {
            SYS_CONTROL_SHUTDOWN -> throw ControlStopSignal(ExecutionResult.Shutdown(false))
            SYS_CONTROL_REBOOT -> throw ControlStopSignal(ExecutionResult.Shutdown(true))
            SYS_CONTROL_CRASH -> {
                val str = intr.readString()
                throw ControlStopSignal(ExecutionResult.Error(str))
            }
            SYS_CONTROL_RETURN -> throw ControlStopSignal(SystemControlReturn)
            SYS_CONTROL_INIT_COPY -> {
                val src = intr.r0
                val dest = intr.r1
                val dest_finish = intr.r2
                val size = dest_finish - dest
                val buffer = intr.readBuffer(src, size)
                intr.memory.writeBuffer(dest, buffer)
                1
            }
            SYS_CONTROL_INIT_ZERO -> {
                val start = intr.r0
                val end = intr.r1
                val size = end - start
                intr.memory.writeBuffer(start, ByteArray(size) { _ -> 0 })
                1
            }
            else -> throw UnknownInterrupt()
        }
    }

    private fun handleDebug(intr: Interrupt, @Suppress("UNUSED_PARAMETER") synchronized: Boolean): Int {
        val buf = intr.readBuffer()
        val str = String(buf, StandardCharsets.UTF_8)

        print(str)
        return 0
    }

    private fun handleSignal(intr: Interrupt, @Suppress("UNUSED_PARAMETER") synchronized: Boolean): Int {
        return when (intr.imm) {
            SYS_SIGNAL_REQUEST -> {
                val signal: Signal? = machine.popSignal()
                if (signal == null) {
                    intr.cpu.regs[R0] = SYS_SIGNAL_PENDING
                    throw ControlPauseSignal(ExecutionResult.Sleep(intr.r0))
                }

                intr.responseValue(signal)
            }
            SYS_SIGNAL_PENDING -> {
                val signal: Signal = machine.popSignal() ?: return 0
                intr.responseValue(signal)
            }
            SYS_SIGNAL_PUSH -> {
                TODO()
            }
            else -> throw UnknownInterrupt()
        }
    }

    private fun handleComponents(intr: Interrupt, synchronized: Boolean): Int {
        return when (intr.imm) {
            SYS_COMPONENTS_INVOKE -> {
                val obj = intr.readObject()
                val call = ComponentInvoke.fromArray(obj as Array<*>)
                        ?: return intr.responseError(Exception("Invaild invoke"))

                if (!synchronized) {
                    val node = machine.node().network().node(call.component) as? Component
                            ?: return intr.responseError(Exception("Invalid Component"))

                    val callback: Callback?

                    try {
                        callback = node.annotation(call.function)
                                ?: return intr.responseError(Exception("Invalid Function"))
                    } catch (e: NoSuchMethodException) {
                        e.printStackTrace()
                        return intr.responseError(e)
                    }

                    if (!callback.direct) {
                        // TODO: automatic detect sync invoke?
                        throw ControlPauseSignal(ExecutionResult.SynchronizedCall())
                    }
                }

                val ret = call(machine)
                ret.error?.printStackTrace()
                intr.responseResult(ret)
            }
            SYS_COMPONENTS_LIST -> {
                when (intr.r0) {
                    0 -> intr.responseValue(machine.components())
                    else -> {
                        val target = intr.readString()
                        val components = ArrayList<String>()
                        for ((address, type) in machine.components()) {
                            if (type == target)
                                components.add(address)
                        }

                        intr.responseValue(components)
                    }
                }
            }
            SYS_COMPONENTS_COUNT -> intr.responseValue(machine.componentCount())
            SYS_COMPONENTS_MAX -> intr.responseValue(machine.maxComponents())
            SYS_COMPONENTS_METHODS -> {
                val req = intr.readObject() as Array<*>
                val node = machine.node().network().node(req[0] as String)

                if (node is Component) {
                    return intr.responseValue(node.methods())
                }

                0
            }
            SYS_COMPONENTS_ANNOTATIONS -> {
                val req = intr.readObject() as Array<*>
                if (req.size == 2) {
                    val node = machine.node().network().node(req[0] as String)
                    if (node is Component) {
                        try {
                            val callback = node.annotation(req[1] as String)
                            return intr.responseValue(callback.doc)
                        } catch (e: NoSuchMethodError) {
                            return 0
                        } catch (exc: Exception) {
                            // how to handle?
                            exc.printStackTrace()
                        }
                    }
                }

                0
            }
            else -> throw UnknownInterrupt()
        }
    }

    private fun handleValue(intr: Interrupt, synchronized: Boolean): Int {
        return when (intr.imm) {
            SYS_VALUE_CALL -> {
                val obj = intr.readObject()
                val call = ValueCall.fromArray(obj as Array<*>)
                        ?: return intr.responseError(Exception("Invaild call"))

                val ret = call(machine)
                ret.error?.printStackTrace()
                intr.responseResult(ret)
            }
            SYS_VALUE_APPLY -> {
                val obj = intr.readObject()
                val call = ValueApply.fromArray(obj as Array<*>)
                        ?: return intr.responseError(Exception("Invaild apply"))

                val ret = call(machine)
                ret.error?.printStackTrace()
                intr.responseResult(ret)
            }
            SYS_VALUE_UNAPPLY -> {
                val obj = intr.readObject()
                val call = ValueUnapply.fromArray(obj as Array<*>)
                        ?: return intr.responseError(Exception("Invaild unapply"))

                val ret = call(machine)
                ret.error?.printStackTrace()
                intr.responseResult(ret)
            }
            SYS_VALUE_DISPOSE -> {
                val value = intr.readObject() as Value
                state.valueMap.unregister(value)
                intr.responseNone()
            }
            else -> throw UnknownInterrupt()
        }
    }

    private fun handleComputer(intr: Interrupt, @Suppress("UNUSED_PARAMETER") synchronized: Boolean): Int {
        when (intr.imm) {
            SYS_COMPUTER_GET_COST_PER_TICK -> return intr.responseValue(machine.costPerTick)
            SYS_COMPUTER_LAST_ERROR -> return intr.responseValue(machine.lastError())
            SYS_COMPUTER_BEEP_1 -> {
                val pattern = intr.readString()
                machine.beep(pattern)
                return intr.responseNone()
            }
            SYS_COMPUTER_BEEP_2 -> {
                machine.beep(intr.r0.toShort(), intr.r1.toShort())
                return intr.responseNone()
            }
            SYS_COMPUTER_USERS -> return intr.responseValue(machine.users())
            SYS_COMPUTER_ADD_USER -> {
                val user = intr.readString()

                return try {
                    machine.addUser(user)
                    intr.responseNone()
                } catch (e: Exception) {
                    intr.responseError(e)
                }
            }
            SYS_COMPUTER_REMOVE_USER -> {
                val user = intr.readString()
                return intr.responseValue(machine.removeUser(user))
            }
            SYS_COMPUTER_COMPUTER_ADDRESS -> return intr.responseValue(machine.node().address())
            SYS_COMPUTER_TMP_ADDRESS -> return intr.responseValue(machine.tmpAddress())
            else -> throw UnknownInterrupt()
        }
    }

    private fun handleInfo(intr: Interrupt, @Suppress("UNUSED_PARAMETER") synchronized: Boolean): Int {
        return when (intr.imm) {
            SYS_INFO_VERSION -> 0x01000000 // 1.0.0.0
            SYS_INFO_RAM_SIZE -> vm.memorySize
            else -> throw UnknownInterrupt()
        }
    }

    private fun handleTimer(intr: Interrupt, @Suppress("UNUSED_PARAMETER") synchronized: Boolean): Int {
        return when (intr.imm) {
            SYS_TIMER_TICKS_MS -> System.currentTimeMillis().toInt()
            SYS_TIMER_TICKS_US -> System.nanoTime().toInt()
            SYS_TIMER_WORLD_TIME -> intr.responseValue(machine.worldTime())
            SYS_TIMER_UP_TIME -> intr.responseValue(machine.upTime())
            SYS_TIMER_CPU_TIME -> intr.responseValue(machine.cpuTime())
            else -> throw UnknownInterrupt()
        }
    }

    private fun handleVirtualFileSystem(intr: Interrupt, synchronized: Boolean): Int {
        if (!synchronized)
            throw ControlPauseSignal(ExecutionResult.SynchronizedCall())

        val command = intr.imm
        if (command == SYS_VFS_OPEN) {
            val address = intr.readString(intr.r0, 64)
            val path = intr.readString(intr.r1, 256)
            val mode = intr.readString(intr.r2, 16)

            val ret = ComponentInvoke(address, "open", path, mode)(machine)
            when {
                ret.error is FileNotFoundException -> return MP_ENOENT
                ret.args != null -> {
                    if (ret.args.size != 1)
                        return MP_EPERM

                    val fdPtr = intr.r3
                    val fd = state.fdCount++
                    val handle = Integer.parseInt(ret.args[0].toString()) // handle

                    state.fdMap[fd] = FileHandle(address, handle)

                    intr.memory.writeInt(fdPtr, fd)
                    return MP_OK
                }
                else -> return MP_EPERM
            }
        } else {
            val fd = intr.r0
            val fh = state.fdMap.getOrDefault(fd, null) ?: return MP_EBADF

            when (command) {
                SYS_VFS_VALID -> {
                    val ret = fh("seek", fh.pos)(machine)
                    return if (ret.error == null) MP_OK else MP_EIO
                }
                SYS_VFS_REPR -> return MP_EPERM
                SYS_VFS_CLOSE -> {
                    val ret = fh("close")(machine)
                    return if (ret.error != null) {
                        ret.error.printStackTrace()
                        1
                    } else {
                        state.fdMap.remove(fd)
                        MP_OK
                    }
                }
                SYS_VFS_READ -> {
                    val ret = fh("read", intr.r1)(machine)
                    when {
                        ret.error != null -> {
                            ret.error.printStackTrace()
                            return 1
                        }
                        ret.args != null -> {
                            if (ret.args.size != 1) {
                                return MP_EPERM
                            }

                            val arg = ret.args[0]
                            return when (arg) {
                                is ByteArray -> {
                                    intr.memory.writeBuffer(intr.r2, arg)
                                    intr.memory.writeInt(intr.r3, arg.size)
                                    0
                                }
                                null -> // EOF
                                    0
                                else -> MP_EPERM
                            }
                        }
                        else -> return MP_EPERM
                    }
                }
                SYS_VFS_WRITE -> {
                    val buf = intr.readBuffer(intr.r1, intr.r2)
                    val ret = fh("write", buf)(machine)
                    return when {
                        ret.error != null -> {
                            ret.error.printStackTrace()
                            1
                        }
                        ret.args != null -> {
                            if (ret.args.size != 1) {
                                MP_EPERM
                            } else {
                                val arg = ret.args[0]
                                when (arg) {
                                    is Boolean -> {
                                        if (arg) {
                                            fh.pos += buf.size
                                            intr.memory.writeInt(intr.r3, buf.size)
                                        }
                                        0
                                    }
                                    null -> MP_OK // EOF
                                    else -> MP_EPERM
                                }
                            }
                        }
                        else -> return MP_EPERM
                    }
                }
                SYS_VFS_SEEK -> {
                    val offset = intr.r1
                    val whence = intr.r2
                    val offsetPtr = intr.r3
                    var whenceStr: String? = null

                    when (whence) {
                        0 // MP_SEEK_SET
                        -> whenceStr = "set"
                        1 // MP_SEEK_CUR
                        -> whenceStr = "cur"
                        2 // MP_SEEK_END
                        -> whenceStr = "end"
                        else -> {
                        }
                    }

                    if (whenceStr == null) {
                        return MP_EPERM
                    }

                    val ret = fh("seek", whenceStr, offset)(machine)
                    return when {
                        ret.error != null -> {
                            ret.error.printStackTrace()
                            1
                        }
                        ret.args != null -> {
                            if (ret.args.size != 1) {
                                MP_EPERM
                            } else {
                                val arg = ret.args[0]
                                if (arg is Int) {
                                    fh.pos = arg
                                    MP_OK
                                } else
                                    MP_EPERM

                            }
                        }
                        else -> MP_EPERM
                    }
                }
                SYS_VFS_FLUSH -> return 0 // always flushed?
                else -> return MP_EPERM
            }
        }
    }
}
