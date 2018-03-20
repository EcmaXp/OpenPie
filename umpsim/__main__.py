import sys
import time
from pathlib import Path

import cstruct
from capstone import *
from unicorn import *
from unicorn.arm_const import *


class UnicornControllerStruct(cstruct.CStruct):
    __byte_order__ = cstruct.LITTLE_ENDIAN
    __struct__ = """
    unsigned int PENDING;
    unsigned int EXCEPTION;
    unsigned int INTR_CHAR;
    unsigned int RAM_SIZE;
    unsigned int STACK_SIZE;
    unsigned int IDLE;
    """


# from www-emu/mp_unicorn.js
FLASH_ADDRESS = 0x08000000
FLASH_SIZE = 0x100000
RAM_ADDRESS = 0x20000000
MAX_RAM_SIZE = 0x40000
PERIPHERAL_ADDRESS = 0x40000000
PERIPHERAL_SIZE = 0x10000
UART0_TXR = 0x40000000
UART0_RXR = 0x40000004

UNICORN_CONTROLLER_PENDING = 0x40000100
UNICORN_CONTROLLER_EXCEPTION = 0x40000104
UNICORN_CONTROLLER_INTR_CHAR = 0x40000108
UNICORN_CONTROLLER_RAM_SIZE = 0x4000010c
UNICORN_CONTROLLER_STACK_SIZE = 0x40000110
UNICORN_CONTROLLER_IDLE = 0x40000114
UNICORN_CONTROLLER_INSNS = 0x40000118

CYCLE_LIMIT = 50000
RAM_SIZE = 1024 * 128
STACK_SIZE = 1024 * 32


firmware = Path("../firmware/firmware_minimal.bin").read_bytes()
emu = Uc(UC_ARCH_ARM, UC_MODE_THUMB)
cs = Cs(CS_ARCH_ARM, CS_MODE_THUMB)

def from_bytes(b):
    return int.from_bytes(b, byteorder="little")

def to_bytes(n):
    return int.to_bytes(n, 4, byteorder="little")

def debug_addr(addr, count=1):
    for inst in cs.disasm(firmware[addr - FLASH_ADDRESS:addr - FLASH_ADDRESS + 2 * count], addr, count):
        print(hex(inst.address), inst.mnemonic, inst.op_str)

source = """
def hello():
return 3



hello()
"""

pending_addr = 0
exception_addr = 0
ichr_addr = 0
stack = list(("\r\n".join(source.strip().splitlines()) + '\r\n').encode())

def hook_read(uc: Uc, access, address, size, value, data):
    if address == UNICORN_CONTROLLER_RAM_SIZE:
        emu.mem_write(address, to_bytes(RAM_SIZE))
    elif address == UNICORN_CONTROLLER_STACK_SIZE:
        emu.mem_write(address, to_bytes(STACK_SIZE))
    elif address == UART0_RXR:
        if stack:
            emu.mem_write(address, to_bytes(stack.pop(0)))
            #if not stack:
            #    emu.mem_write(pending_addr, exception_addr)
    else:
        print("read", access, hex(address), size, value, data)


def hook_write(uc: Uc, access, address, size, value, data):
    global pending_addr, exception_addr, ichr_addr
    if address == UNICORN_CONTROLLER_PENDING:
        pending_addr = addr
        print("UNICORN_CONTROLLER_PENDING", value)
    elif address == UNICORN_CONTROLLER_EXCEPTION:
        print("UNICORN_CONTROLLER_EXCEPTION", value)
        exception_addr = to_bytes(value)
    elif address == UNICORN_CONTROLLER_INTR_CHAR:
        print("UNICORN_CONTROLLER_INTR_CHAR", value)
        ichr_addr = value
    elif address == UART0_TXR:
        print(chr(value), end="")
        sys.stdout.flush()
    else:
        print("write", access, hex(address), size, value, data)

#if 1:
#    addr = 0x08000000
#    for i in range(100):
#        debug_addr(addr, 1000)
#        addr += 1000
#
#    exit()

addr = 0

try:
    emu.mem_map(FLASH_ADDRESS, FLASH_SIZE, UC_PROT_ALL)
    emu.mem_map(RAM_ADDRESS, MAX_RAM_SIZE, UC_PROT_ALL)
    emu.mem_map(PERIPHERAL_ADDRESS, PERIPHERAL_SIZE, UC_PROT_ALL)

    sp = RAM_ADDRESS + RAM_SIZE
    addr = from_bytes(firmware[4:8])
    emu.mem_write(FLASH_ADDRESS, firmware)
    emu.mem_write(FLASH_ADDRESS, to_bytes(sp))

    emu.hook_add(UC_HOOK_MEM_READ, hook_read, None, PERIPHERAL_ADDRESS, PERIPHERAL_ADDRESS + PERIPHERAL_SIZE)
    emu.hook_add(UC_HOOK_MEM_WRITE, hook_write, None, PERIPHERAL_ADDRESS, PERIPHERAL_ADDRESS + PERIPHERAL_SIZE)

    emu.reg_write(UC_ARM_REG_PC, addr)

    CYCLE_LIMIT = 1000

    while True:
        addr = emu.reg_read(UC_ARM_REG_PC)
        emu.emu_start(addr | 1, FLASH_ADDRESS + FLASH_SIZE, 0, 10000)
        # debug_addr(addr)


except UcError as e:
    print("ERROR:", e)
    debug_addr(addr - 8, count=3)
    print(">", end=" ")
    debug_addr(addr)
    debug_addr(addr + 2, count=3)
    print(hex(emu.reg_read(UC_ARM_REG_R3)))
    print(hex(emu.reg_read(UC_ARM_REG_R0)))
    print(hex(emu.reg_read(UC_ARM_REG_R4)))
