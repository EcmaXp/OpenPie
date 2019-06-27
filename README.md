# OpenPython

![Python Interpreter in OpenPython v1.0](https://user-images.githubusercontent.com/21021916/47162981-0163aa80-d330-11e8-9e54-7b470a5e67b0.png)
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2FOpenPythons%2FOpenPython.svg?type=shield)](https://app.fossa.io/projects/git%2Bgithub.com%2FOpenPythons%2FOpenPython?ref=badge_shield)

**OpenPython makes micropython available on OpenComputers.**

micropython is Python for embedded devices and is different from CPython.
Currently, micropython is partially compatible with Python version 3.4 as of October 2018.

Features include:
- Fully persistable.
- Runs in a sandboxed environment and does not require a separate native DLL.
- Supports virtual file system.
- It can call other components and supports Lua architecture's UserData.

Limitations include:
- The firmware is 256 KB and the memory limit has the same limit as the Lua architecture, but the stack is allocated in a separate memory.
- Most modules are difficult to use because the battery is partially included.
- The operating system is not yet fully implemented, so the Python interpreter now runs.
- Ctrl + C is not supported, so if you accidentally run infinite repeated code, the only way to stop it is to turn the computer off and then on.

To use the Python architecture, you need the following items:
- EEPROM (OpenPython): I'm sorry, but you have to bring items from the Creative tab.
- Floppy Disk (OpenPython OS): This item can be obtained by creating a floppy disk and Scrench, or you can check it on the Creative tab.
- OpenPython CPU: You can choose OpenPython architecture by holding Shift + Right Click on the Lua architecture CPU.

It is still unstable, but we plan to make further improvements in the future.

I need help from people who are familiar with OpenComputers and Python.

Thank you for playing.

Links
- [minecraft.curseforge.com/projects/openpython](https://minecraft.curseforge.com/projects/openpython)
- [OpenPython in oc.cil.li](https://oc.cil.li/index.php?/topic/1744-mc1122oc17-openpython%C2%A0micropython-available-on-opencomputers/)


## License
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2FOpenPythons%2FOpenPython.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2FOpenPythons%2FOpenPython?ref=badge_large)