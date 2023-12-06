var emulator;

window.onload = function() {
	var params = Object.create(null);
	var pairs = window.location.search.substr(1).split("&");
	for (var i = 0, n = pairs.length; i < n; ++i) {
		var keyAndValue = pairs[i].split("=");
		params[keyAndValue[0]] = keyAndValue[1];
	}
	if (!params.width && !params.height) {
		var h = window.innerHeight - document.getElementById("screen").offsetTop;
		if (window.innerWidth < 1024 || h < 512) {
			params.width = 800;
			params.height = h < 450 ? 400 : h < 600 ? 450 : 600;
		} else {
			params.width = 1024;
			params.height = h < 576 ? 512 : h < 768 ? 576 : 768;
		}
	}
	params.width = +params.width;
	params.height = +params.height;
	if ((params.width % 32) != 0) {
		params.width -= params.width % 32;
	}
	params.mem = +params.mem;
	params.dismem = +params.dismem;
	if ([1,2,4,8,16,32,64].indexOf(params.mem) == -1) {
		params.mem = 1;
	}
	if ([96,256,384,512,1024,2048].indexOf(params.dismem) == -1 || params.dismem > params.mem * 384) {
		params.dismem = 96;
	}
	if (params.width / 32 * params.height > params.dismem * 1024) {
		params.width = 1024;
		params.height = 768;
	}
	if (params.serialpreview) {
		document.body.classList.add(params.serialpreview.replace(/,.*/,"")+"preview");
	}
	RISCMachine.Initialize(function(uri, callback) {
		var request = new XMLHttpRequest();
		request.addEventListener("load", function(event) {
			callback(event.target.response);
		});
		request.open("GET", uri);
		request.responseType = "arraybuffer";
		request.send(null);
	}, function() {
		emulator = new WebDriver(params.image, params.width, params.height, params.pclink == "2", params.configfile || "config.json", params.mem, params.dismem);
	});
}

function WebDriver(imageName, width, height, dualSerial, configFile, mem, dismem) {
	this.disk = [];
	this.keyBuffer = [];
	this.keyEventBuffer = [];
	this.transferHistory = [];

	this.localSaveAnchor = document.getElementById("localsaveanchor");
	this.screen = document.getElementById("screen");
	this.previewscreen = document.getElementById("previewscreen");
	this.previewcontext = this.previewscreen.getContext("2d");
	this.previewcontext.fillStyle = "black";

	this.ui = new ControlBarUI(this, dualSerial);
	this.setDimensions(width, height, true);
	if (imageName !== undefined) {
		this.imageName = imageName;
	}

	this.resolutions = [{w:800,h:600,c:false},{w:1024,h:768,c:false},{w:1344,h:768,c:false},{w:1280,h:1024,c:false},
		{w:1400,h:1050,c:false},{w:1600,h:900,c:false},{w:1600,h:1200,c:false},{w:1920,h:1080,c:false}];

	var foundResolution = false;
	for(var i = 0; i < this.resolutions.length; i++) {
		if (this.resolutions[i].w == width && this.resolutions[i].h == height) {
			foundResolution = true;
		}
		if (this.resolutions[i].w / 32 * this.resolutions[i].h > dismem * 1024) {
			this.resolutions.splice(i, 1);
			i--;
		}
	}
	if (!foundResolution) {
		this.resolutions.push({w: width, h: height, c: false});
	}
	this.resolutions.monoCount = this.resolutions.length;
	for(var i = 0; i < this.resolutions.monoCount; i++) {
		if (this.resolutions[i].w / 8 * this.resolutions[i].h <= dismem * 1024) {
			this.resolutions.push({w: this.resolutions[i].w, h: this.resolutions[i].h, c: true});
		}
	}

	document.querySelector(".ramhint").value = mem;
	document.querySelector(".disphint").value = dismem;

	this.clipboard = new Clipboard(this.ui.clipboardInput);
	this.virtualKeyboard = new VirtualKeyboard(this.screen, this);
	this.filelink = new FileLink(this);
	this.link = dualSerial ? new DualLink(this.filelink, this.filelink) : this.filelink;
	this.bootFromSerial = false;

	if (window.offlineInfo) {
		if (/https?:\/\/[^\/]+\//.test(window.location)) {
			this.wiznet = new WizNet(this);
			var request = new XMLHttpRequest();
			request.addEventListener("load", function(event) {
				window.offlineInfo.netConfig = JSON.parse(event.target.responseText);
			});
			request.open("GET", "/net/config.json");
			request.send(null);
		}
		this.useConfiguration(offlineInfo.config, offlineInfo.rom);
	} else {
		SiteConfigLoader.read(configFile, this);
	}
}

(function(){
	var $proto = WebDriver.prototype;

	// This is a callback suitable for use with `setTimeout` so that `this`
	// will resolve correctly within the `run` method body.  We would use
	// `Function.prototype.bind`, but we want to be compatible with IE10.
	WebDriver.$run = $proto.$run = function(self) {
		self.run();
	};

	$proto.localSaveAnchor = null;
	$proto.screen = null;

	$proto.inputEmulation = true;
	$proto.activeButton = 1;
	$proto.clipboard = null;
	$proto.cpuTimeout = null;
	$proto.disk = null;
	$proto.height = 0;
	$proto.imageName = null;
	$proto.interclickButton = 0;
	$proto.keyBuffer = null;
	$proto.machine = null;
	$proto.mouse = null;
	$proto.paravirtPointer = 0;
	$proto.paused = false;
	$proto.screenUpdater = null;
	$proto.startMillis = null;
	$proto.transferHistory = null;
	$proto.width = 0;
	$proto.autosave = false;
	$proto.debugBuffer = "";
	$proto.rom = null;

	$proto.setDimensions = function(width, height, resizeControlBar) {
		this.width = width || 1024;
		this.height = height || 768;
		if (this.width > 1024) {
			var ramhint = +document.querySelector(".ramhint").value;
			var disphint = +document.querySelector(".disphint").value;
			if (disphint < 512) {
				document.querySelector(".disphint").value = "512";
				if (ramhint < 2) {
					document.querySelector(".ramhint").value = "2";
				}
			}
		}
		if (resizeControlBar) {
			this.ui.resize(this.width, this.height);
		}
		var size = this.width + "×" + this.height;
		this.ui.selectItem(this.ui.settingsButton, "size", size);
	};

	$proto.setDimensionsFromEvent = function(event) {
		var dimensions = event.target.value.split("×");
		this.setDimensions(dimensions[0], dimensions[1], true);
		this.ui.closeOpenPopups();
	};

	$proto.useConfiguration = function(config, rom) {
		var images = config.images;
		if (window.localStorage !== undefined && window.localStorage.getItem("AUTOSAVE") !== null) {
			this.ui.addSystemItem("(Autosaved)");
		}
		this.rom = rom;
		for (var i = 0; i < images.length; ++i) {
			this.ui.addSystemItem(images[i]);
		}
		if (this.imageName !== null) {
			this.chooseDisk(this.imageName);
		}
	};

	$proto.chooseDisk = function(name) {
		this.ui.markLoading();
		this.ui.closeOpenPopups();
		var item = this.ui.selectItem(this.ui.systemButton, "diskimage", name);
		if (name == "(Autosaved)") {
			var contents = [];
			var size = window.localStorage.getItem("AUTOSAVE")-0;
			for (var i = 0; i < size; i++) {
				var arr = Uint8Array.from(atob(window.localStorage.getItem("AUTOSAVE-"+i)), function(c) { return c.charCodeAt(0)});
				contents[i] = new Int32Array(arr.buffer);
			}
			this.useSystemImage("(Autosaved)", contents);
			this.autosave = true;
			this.ui.autosaveToggle.classList.add("checked");
		} else if (item !== null) {
			// It's one of our premade images, not user supplied.
			this.reader = new PNGImageReader(name);
			this.reader.prepareContentsThenNotify(this);
		}
	};

	// The system image format uses 1024-byte sectors in the format used for
	// Peter De Wachter's RISC emulator.
	$proto.useSystemImage = function(name, contents) {
		if (this.autosave) {
			this.autosave = false;
			this.ui.autosaveToggle.classList.remove("checked");
		}
		this.ui.markLoading();
		if (/.*\.dsk/.test(name))
			name = name.substring(0, name.length-4);
		this.imageName = name;
		this.ui.markName(this.imageName);
		this.bootFromDisk(contents);
	};

	$proto._hasDirMark = function(contents, sectorNumber) {
		var view = new DataView(contents[sectorNumber].buffer);
		return view.getUint32(0) === 0x8DA31E9B;
	};

	$proto.updateSelectBox = function(box, html) {
		var oldValue = box.value;
		box.innerHTML = html;
		var newValue = box.value;
		box.value = oldValue;
		if (box.value != oldValue)
			box.value = newValue;
	}

	$proto.bootFromDisk = function(disk) {
		if (!this.isDisplayReady()) {
			this.setUpDisplay(this.width, this.height, false);
			this.screen.addEventListener("mousemove", this, false);
			this.screen.addEventListener("mousedown", this, false);
			this.screen.addEventListener("mouseup", this, false);
			this.screen.addEventListener("contextmenu", this, false);
		}
		this.disk = disk;
		this.startMillis = Date.now();
		var that = this;
		this.machine = new RISCMachine(this.rom);
		this.machine.Initialize(function() {
			that.reset(true, false);
		});
	};

	$proto.isDisplayReady = function() {
		return this.screenUpdater !== null;
	};

	$proto.setUpDisplay = function(width, height, dynamic) {
		this.screen.focus();
		this.screen.width = width;
		this.screen.height = height;
		this.screenStride = (dynamic || this.screen.width > 1024) ? this.screen.width / 32 : 32;
		this.screenUpdater = new ScreenUpdater(this.screen, width, height);
		this.previewscreen.width = width / 5;
		this.previewscreen.height = height / 5;
		this.previewcontext.setTransform(0.2, 0, 0, 0.2, 0, 0)
		this.previewcontext.fillRect(0,0, width, height);
	};

	$proto.reset = function(cold, serialBoot) {
		var ramhint = +document.querySelector(".ramhint").value;
		var disphint = +document.querySelector(".disphint").value;
		this.bootFromSerial = serialBoot;
		this.machine.setStall(false);
		this.machine.cpuReset(cold, ramhint, disphint);
		if (cold) {
			var base = this.machine.getDisplayStart();
			this.machine.memWriteWord(base, 0x53697A65); // magic value 'Size' -- legacy
			this.machine.memWriteWord(base + 4, this.screen.width);
			this.machine.memWriteWord(base + 8, this.screen.height);

			var d = new Date(Math.floor(Date.now() / 1000) * 1000);
			var clock = ((d.getYear() % 100) * 16 + d.getMonth() + 1) * 32 + d.getDate();
			clock = ((clock * 32 + d.getHours()) * 64 + d.getMinutes()) * 64 + d.getSeconds();
			this.rtchint = [d.getTime() - this.startMillis, clock];
		}
		this.reschedule();
	};

	$proto.reschedule = function() {
		if (this.cpuTimeout != null) window.clearTimeout(this.cpuTimeout);
		this.cpuTimeout = window.setTimeout(this.$run, 1, this);
	};

	$proto.run = function() {
		if (this.paused) return;
		this.machine.cpuRun();
		this.cpuTimeout = window.setTimeout(
			this.$run, Math.max(this.machine.getWaitMillis() - Date.now(), 10), this
		);
	};

	$proto.getTickCount = function() {
		return Date.now() - this.startMillis;
	};

	$proto.registerVideoChange = function(offset, value, palette) {
		if (palette != null) {
			var x = (offset % (this.screenStride * 4)) * 8;
			var y = this.screen.height - 1 - (offset / (this.screenStride * 4) | 0);
			if (y < 0 || x >= this.screen.width) return;
			var base = (y * this.screen.width + x) * 4;
			var data = this.screenUpdater.backBuffer.data;
			for (var i = 0; i < 8; i++) {
				var col = palette[(value >>> (i*4)) & 0xF];
				data[base++] = col >>> 16;
				data[base++] = (col >>> 8) & 0xFF;
				data[base++] = col & 0xFF;
				data[base++] = 255;
			}
			this.screenUpdater.mark(x, y);
			return;
		}
		var x = (offset % (this.screenStride)) * 32;
		var y = this.screen.height - 1 - (offset / this.screenStride | 0);
		if (y < 0 || x >= this.screen.width) return;
		var base = (y * this.screen.width + x) * 4;
		var data = this.screenUpdater.backBuffer.data;
		for (var i = 0; i < 32; i++) {
			var lit = ((value & (1 << i)) != 0);
			data[base++] = lit ? 0xfd : 0x65;
			data[base++] = lit ? 0xf6 : 0x7b;
			data[base++] = lit ? 0xe3 : 0x83;
			data[base++] = 255;
		}
		this.screenUpdater.mark(x, y);
	};

	$proto.registerMousePosition = function(x, y) {
		var before = this.mouse;

		var after = this.mouse;
		if (0 <= x && x < 4096) after = (after & ~0x00000FFF) | x;
		if (0 <= y && y < 4096) after = (after & ~0x00FFF000) | (y << 12);

		if (before === after) return;

		this.mouse = after;
		this.machine.resetWaitMillis();
		this.reschedule();
	};

	$proto.registerMouseButton = function(button, down) {
		if (1 <= button && button <= 3) {
			var bit = 1 << (27 - button);
			if (down) {
				this.mouse |= bit;
			}
			else {
				this.mouse &= ~bit;
			}
		}
		this.machine.resetWaitMillis();
		this.reschedule();
	};

	$proto.registerLEDs = function(bitstring) {
		for (var i = 0; i < 8; i++) {
			this.ui.setLEDState(i, (bitstring & (1 << i)));
		}
	};

	$proto.storageRequest = function(value, memory) {
		if ((value & 0xC0000000) === 0) {
			// set pointer
			this.paravirtPointer = value | 0;
			return;
		}

		// NB: The actual index for our disk will be off by one because the
		// given sector number includes the phantom boot sector (reserved at
		// sector 0) but which is not actually part of the disk.
		var sectorNumber = value & 0x3FFFFFFF;
		var address = this.paravirtPointer / 4;
		if ((value & 0xC0000000) === (0x80000000 | 0)) {
			// read
			var sector = this.disk[sectorNumber - 1];
			if (!sector) sector = new Int32Array(256);
			memory.set(sector, address);
			return;
		}
		if ((value & 0xC0000000) === (0xC0000000 | 0)) {
			if (this.paravirtPointer == 0x3FFFFFFF) {
				// trim
				if (this.disk.length > sectorNumber - 1)
					this.disk.length = sectorNumber - 1;
			} else {
				// write
				var sector = new Int32Array(256);
				sector.set(memory.subarray(address, address + 256));
				this.disk[sectorNumber - 1] = sector;
			}
			if (this.autosave) {
				window.localStorage.setItem("AUTOSAVE", this.disk.length);
				window.localStorage.setItem("AUTOSAVE-"+(sectorNumber-1), btoa(String.fromCharCode.apply(null, new Uint8Array(this.disk[sectorNumber-1].buffer))));
			} else if (this.machine.markDiskChanges) {
				this.ui.setModified(true);
			}
			return;
		}
	};

	$proto.consoleCommand = function(value) {
		if (value == 0) {
			if (this.debugBuffer != "") {
				console.log(this.debugBuffer.replace("\r", "\n"));
			}
			this.debugBuffer = "";
		} else {
			this.debugBuffer += String.fromCharCode(value);
		}
	}

	$proto.netCommand = function(value, memory) {
		if (!window.offlineInfo || !window.offlineInfo.netConfig || !this.wiznet) {
			return;
		}
		this.wiznet.netCommand(value, memory);
	};

	$proto.toHardwareId = function(str) {
		return (str.charCodeAt(0) << 24) | (str.charCodeAt(1) << 16) | (str.charCodeAt(2) << 8) | str.charCodeAt(3);
	};

	$proto._runHardwareEnumerator = function(value) {
		var wiznet = window.offlineInfo && window.offlineInfo.netConfig && this.wiznet;
		switch(value) {
			case 0:
				var result = [1 /*version*/, 'mVid', 'mDyn', 'Timr', 'LEDs', 'SPrt', 'MsKb', 'vClp', 'vRTC', 'vDsk', 'Rset', 'DbgC', 'DChg', 'JSKb'];
				if (wiznet) {
					result.push('vNet');
				}
				if (this.machine.colorSupported) {
					result.push('16cV', '16cD');
				};
				if ('InvalidateCodeCache' in this.machine) {
					result.push('ICIv');
				}
				return result;
			case this.toHardwareId('mVid'):
				var displayStart = this.machine.getDisplayStart();
				var result = [this.resolutions.monoCount, -16];
				for(var i = 0; i < this.resolutions.monoCount; i++) {
					var r = this.resolutions[i];
					result.push(r.w, r.h, r.w <= 1024 ? 128 : r.w / 8, displayStart);
				}
				return result;
			case this.toHardwareId('16cV'):
				if (this.machine.colorSupported && this.resolutions.length > this.resolutions.monoCount) {
					var paletteStart = this.machine.getRAMSize() - 0x80;
					var displayStart = this.machine.getDisplayStart();
					var result = [this.resolutions.length - this.resolutions.monoCount, this.resolutions.monoCount, -16, paletteStart];
					for(var i = this.resolutions.monoCount; i < this.resolutions.length; i++) {
						var r = this.resolutions[i];
						result.push(r.w, r.h, r.w <= 1024 ? 512 : r.w / 2, displayStart);
					}
					return result;
				} else {
					return [];
				}
			case this.toHardwareId('mDyn'):
				var displayStart = this.machine.getDisplayStart();
				var dispMemPixels = (this.machine.getRAMSize() - displayStart - 0x100) * 32;
				var maxWidth=4096, maxHeight=4096;
				while (dispMemPixels < maxWidth * maxHeight / 2) {
					maxWidth >>=1; maxHeight >>=1;
				}
				if (dispMemPixels < maxWidth * maxHeight) {
					maxHeight = dispMemPixels / maxWidth;
				}
				return [-16, maxWidth, maxHeight, 32, 1, -1, displayStart, 1];
			case this.toHardwareId('16cD'):
				if (this.machine.colorSupported) {
					var paletteStart = this.machine.getRAMSize() - 0x80;
					var displayStart = this.machine.getDisplayStart();
					var dispMemPixels = (this.machine.getRAMSize() - displayStart - 0x100) * 2;
					var maxWidth=4096, maxHeight=4096;
					while (dispMemPixels < maxWidth * maxHeight / 2) {
						maxWidth >>=1; maxHeight >>=1;
					}
					if (dispMemPixels < maxWidth * maxHeight) {
						maxHeight = dispMemPixels / maxWidth;
					}
					return [-16, paletteStart, maxWidth, maxHeight, 32, 1, -1, displayStart, 1];
				} else {
					return [];
				}
			case this.toHardwareId('Rset'):
				var romStart = this.machine.getRAMSize() - 0x2000;
				return [romStart];
			case this.toHardwareId('SPrt'):
				var portCount = (this.link instanceof DualLink) ? 2 : 1;
				return [portCount, -52, -56];
			case this.toHardwareId('Boot'):
				return [this.machine.getDisplayStart() - 0x10, this.bootFromSerial ? 1 : 0];
			case this.toHardwareId('ICIv'):
				if ('InvalidateCodeCache' in this.machine) {
					return [-40];
				} else {
					return [];
				}
			case this.toHardwareId('vRTC'): return this.rtchint;
			case this.toHardwareId('vNet'): return wiznet ? [-32] : [];
			case this.toHardwareId('DbgC'): return [-12];
			case this.toHardwareId('Timr'): return [-64, 1];
			case this.toHardwareId('LEDs'): return [8, -60];
			case this.toHardwareId('MsKb'): return [-40, -36, 1];
			case this.toHardwareId('vClp'): return [-24, -20];
			case this.toHardwareId('vDsk'): return [-28];
			case this.toHardwareId('DChg'): return [-8];
			case this.toHardwareId('JSKb'): return [-36];
			default: return [];
		}
	};

	$proto.runHardwareEnumerator = function(value) {
		var that = this;
		return this._runHardwareEnumerator(value).map(function(v) {
			return (typeof v == 'string' && v.length == 4) ? that.toHardwareId(v) : v;
		});
	}

	$proto.setVideoMode = function(value) {
		if (value >= 0 && value < this.resolutions.length) {
			var r = this.resolutions[value];
			if (this.screen.width != r.w || this.screen.height != r.h) {
				this.setDimensions(r.w, r.h, true);
				this.setUpDisplay(r.w, r.h, false);
			}
			return r.c;
		} else if ((value & 0x40000000) == 0x40000000) {
			var width = (value >>> 15) & ((1 << 15) - 1);
			var height = value & ((1 << 15) - 1);
			if (width == 0 && height == 0) {
				height = window.innerHeight - emulator.screen.offsetTop - 32;
				width = window.innerWidth - emulator.screen.offsetLeft - 8;
				width -= (width % 32);
			}
			if (this.screen.width != width || this.screen.height != height) {
				this.setDimensions(width, height, true);
				this.setUpDisplay(width, height, true);
			}
			return value < 0;
		}
		return false;
	}

	$proto.getVideoMode = function() {
		for(var i = 0; i < this.resolutions.length; i++) {
			var r = this.resolutions[i];
			if (this.screen.width == r.w && this.screen.height == r.h && (this.machine.palette != null) == r.c) {
				return i;
			}
		}
		return (this.machine.palette != null ? 0xC0000000: 0x40000000) | (this.screen.width << 15) | this.screen.height;
	}

	$proto.registerKey = function(keyCode) {
		this.keyBuffer.push((keyCode << 24) | (keyCode >>> 8 << 16));
		this.machine.resetWaitMillis();
		this.reschedule();
	};

	$proto.registerRawEvent = function(type, evt) {
		if (this.machine.rawEventAddress == 0)
			return false;
		var value = (evt.key == "" ? 0 : evt.key.length == 1 ? evt.key.charCodeAt(0) : 0xffff)
			| (evt.shiftKey ? (1 << 16) : 0)
			| (evt.ctrlKey ? (1 << 17) : 0)
			| (evt.altKey ? (1 << 18) : 0)
			| (evt.metaKey ? (1 << 19) : 0)
			| (evt.isComposing ? (1 << 20) : 0)
			| (evt.repeat ? (1 << 21) : 0)
			| (type << 22) | (evt.location << 24);
		this.keyEventBuffer.push({key: evt.key, code: evt.code, val: value});
		this.machine.resetWaitMillis();
		this.reschedule();
		return true;
	};

	$proto.hasInput = function() {
		if (this.machine.rawEventAddress != 0)
			return this.keyEventBuffer.length > 0;
		return this.keyBuffer.length > 0;
	};

	$proto.getInputStatus = function() {
		if (!this.hasInput()) return this.mouse;
		return this.mouse | 0x10000000;
	};

	$proto._setNameString = function(memory, str, offset, length) {
		var ary, padded = new Uint8Array(length);
		if ('TextEncoder' in window) {
			ary = new TextEncoder().encode(str);
		} else {
			ary = Uint8Array.from(str, function(c) { return c.charCodeAt(0)});
		}
		var len = Math.min(ary.length, length - 1);
		padded.subarray(0, len).set(ary.subarray(0, len));
		memory.subarray(offset/4, offset/4 + length/4).set(new Uint32Array(padded.buffer));
	};

	$proto.getKeyCode = function() {
		if (this.machine.rawEventAddress != 0) {
			var evt = this.keyEventBuffer.length > 0 ? this.keyEventBuffer.shift() : {key:"", code:"", val: 0};
			var memory = this.machine.getMainMemory();
			this._setNameString(memory, evt.code, this.machine.rawEventAddress, 32);
			this._setNameString(memory, evt.key, this.machine.rawEventAddress + 32, 16);
			return evt.val;
		}
		if (!this.hasInput()) return 0;
		return this.keyBuffer.shift();
	};

	$proto.importDiskImage = function(file) {
		if (file === undefined) file = this.ui.diskFileInput.files[0];
		this.ui.markLoading();
		this.chooseDisk(null);
		this.ui.diskFileInput.value = "";
		var reader = new DiskFileReader(file);
		reader.prepareContentsThenNotify(this);
	};

	$proto.importDiskImageFromEvent = function(event) {
		this.cancelEvent(event);
		this.importDiskImage(event.dataTransfer.files[0]);
	};

	$proto.exportDiskImage = function() {
		if (window.event.shiftKey) {
			this.ui.systemButton.value = prompt("Image name", this.ui.systemButton.value);
		}
		this.ui.setModified(false);
		this.save(this.ui.systemButton.value + ".dsk", this.disk);
	};

	$proto.toggleAutoSave = function() {
		this.autosave = !this.autosave;
		this.ui.closeOpenPopups();
		this.ui.autosaveToggle.classList.toggle("checked");
		if (this.autosave) {
			window.localStorage.setItem("AUTOSAVE", this.disk.length);
			for(var i = 0; i < this.disk.length; i++) {
				window.localStorage.setItem("AUTOSAVE-"+i, btoa(String.fromCharCode.apply(null, new Uint8Array(this.disk[i].buffer))));
			}
			this.ui.setModified(false);
		} else if (confirm("Delete existing autosave image?")) {
			window.localStorage.clear();
		}
	}

	$proto.save = function(name, content) {
		var blob = new Blob(content, { type: "application/octet-stream" });
		this.localSaveAnchor.setAttribute("download", name);
		this.localSaveAnchor.href = URL.createObjectURL(blob);
		try {
			this.localSaveAnchor.click();
		} catch (ex) {
			var workaroundSuccess = false;
			// IE10/IE11 *will* fail.  Use a workaround, if possible:
			if (typeof(navigator.msSaveBlob) !== "undefined") {
				workaroundSuccess = navigator.msSaveBlob(blob, name);
			}
			if (!workaroundSuccess) {
				alert("Browser doesn't support file save:\n\n" + ex);
			}
		}
		this.localSaveAnchor.removeAttribute("href");
		this.localSaveAnchor.removeAttribute("download");
	};

	$proto.importFiles = function(files) {
		if (files === undefined) files = this.ui.linkFileInput.files;
		for (var i = 0; i < files.length; ++i) {
			this.filelink.queue(new SupplyTransfer(files[i]));
		}
	};

	$proto.importFilesFromEvent = function(event) {
		this.cancelEvent(event);
		this.importFiles(event.dataTransfer.files);
	};

	$proto.exportFile = function() {
		var names = this.ui.linkNameInput.value.split(/\s+/);
		for (var i = 0; i < names.length; ++i) {
			if (names[i].indexOf("*") != -1) this.filelink.queue(new GlobTransfer(this.filelink, names[i]));
			else if (names[i]) this.filelink.queue(new DemandTransfer(names[i]));
		}
	};

	$proto.completeTransfer = function(transfer) {
		// TODO: Add UI to retransmit files from the transfer history.
		this.transferHistory.push(transfer);
		if (transfer.success === this.filelink.NAK) {
			alert("Transfer failed: " + transfer.fileName);
		} else if (transfer.type === this.filelink.DEMAND_TRANSFER) {
			this.save(transfer.fileName, transfer.blocks);
		}
	};

	// DOM Event handling

	$proto.handleEvent = function(event) {
		switch (event.type) {
			case "mousemove": return void(this._onMouseMove(event));
			case "mousedown": return void(this._onMouseButton(event));
			case "mouseup": return void(this._onMouseButton(event));
			case "contextmenu": return void(event.preventDefault());
			default: throw new Error("got event " + event.type);
		}
	};

	$proto.cancelEvent = function(event) {
		event.stopPropagation();
		event.preventDefault();
	};

	$proto._onMouseMove = function(event) {
		var root = document.documentElement;
		var deltaH = root.scrollLeft - this.screen.offsetLeft;
		var deltaV = root.scrollTop - this.screen.offsetTop;
		var x = event.clientX + deltaH;
		var y = -(event.clientY + deltaV) + this.screen.height - 1;
		this.registerMousePosition(x, y);
	};

	$proto._onMouseButton = function(event) {
		this.ui.closeOpenPopups();
		var button = event.button + 1;
		if (button === 2) event.preventDefault();
		if (event.type === "mousedown") {
			if (this.inputEmulation && button === 1) button = this.activeButton;
			this.registerMouseButton(button, true);
		}
		else {
			if (this.inputEmulation && button === 1) {
				if (this.interclickButton !== 0) {
					this.registerMouseButton(this.interclickButton, true);
					var that = this;
					window.setTimeout(function() {
						that.registerMouseButton(that.interclickButton, false);
						that.registerMouseButton(that.activeButton, false);
					}, 10);
					return;
				}
				button = this.activeButton;
			}
			this.registerMouseButton(button, false);
		}
	};
})();

function ControlBarUI(emulator, dualSerial) {
	this.emulator = emulator;
	this._initWidgets(dualSerial);

	this.linkNameInput.addEventListener("keypress", this, false);
}

(function(){
	var $proto = ControlBarUI.prototype;

	$proto.buttonBox = null;
	$proto.emulCheckbox = null;
	$proto.clickLeft = null;
	$proto.clickMiddle = null;
	$proto.clickRight = null;
	$proto.clipboardInput = null;
	$proto.clipboardToggle = null;
	$proto.autosaveToggle = null;
	$proto.controlBarBox = null;
	$proto.diskFileInput = null;
	$proto.diskImportButton = null;
	$proto.leds = null;
	$proto.linkExportButton = null;
	$proto.linkFileInput = null;
	$proto.linkNameInput = null;
	$proto.settingsButton = null;
	$proto.systemButton = null;

	$proto._initWidgets = function(dualSerial) {
		var $ = document.getElementById.bind(document);
		this.leds = [
			$("led0"), $("led1"), $("led2"), $("led3"),
			$("led4"), $("led5"), $("led6"), $("led7")
		];

		this.buttonBox = $("buttonbox");
		this.emulCheckbox = $("emulcheckbox");
		this.modifyIndicator = $("modifyIndicator");
		this.clipboardInput = $("clipboardinput");
		this.clipboardToggle = $("clipboardToggle");
		this.autosaveToggle = $("autosaveToggle");
		this.controlBarBox = $("controlbar");
		this.settingsButton = $("settingsbutton");
		this.systemButton = $("systembutton");

		this.diskFileInput = $("diskfileinput");
		this.diskImportButton = $("diskimportbutton");

		this.linkFileInput = $("linkfileinput");
		this.linkNameInput = $("linknameinput");
		this.linkExportButton = $("linkexportbutton");

		$ = document.querySelector.bind(document);
		this.clickLeft = $(".mousebtn[name='1']");
		this.clickMiddle = $(".mousebtn[name='2']");
		this.clickRight = $(".mousebtn[name='3']");

		this.linkExportButton.style.height =
			this.linkNameInput.offsetHeight + "px";
		this.linkExportButton.style.width =
			this.linkExportButton.offsetWidth + "px";

		if (dualSerial)
			this.controlBarBox.classList.add('dualserial');

		// XXX Hack to reposition mouse buttons.  Gecko/WebKit/Blink can't
		// seem to agree with IE on how to lay things out based on our CSS,
		// but what they can agree on is making the mouse buttons vertically
		// translated a few pixels down from where we actually want them to be.
		// Note that this fixes things for modern Gecko/WebKit/Blink, but they
		// remain out of place (but not unusably so) on IE and older Chrome.
		var adjustment = this.buttonBox.offsetTop - this.clickLeft.offsetTop;
		if (adjustment !== 0) {
			this.buttonBox.parentNode.style.position = "relative";
			this.buttonBox.style.position = "absolute";
			this.buttonBox.style.right = "20px";
			this.buttonBox.style.top = adjustment + "px";
			this.leds[0].parentNode.style.marginRight =
				(this.buttonBox.offsetWidth + 4) + "px";
		}
	};

	$proto.markLoading = function() {
		this.systemButton.value = "Loading…";
		this.systemButton.classList.add("feedback");
		this.controlBarBox.classList.remove("preflight");
		this.controlBarBox.classList.add("started");
		this.controlBarBox.querySelector(".endcontrols .menu").style.width="";
		this.setModified(false);
	};

	$proto.markName = function(name) {
		this.systemButton.classList.remove("feedback");
		this.systemButton.value = name;
	};

	$proto.resize = function(width, height) {
		this.controlBarBox.style.width = width + "px";
		this.clipboardInput.style.width = width + "px";
		document.getElementById("emulatorface").style.width = (width - -5)+"px";
	};

	$proto.addSystemItem = function(name) {
		var button = document.createElement("button");
		var popup = this.systemButton.parentNode.querySelector(".popup");
		popup.insertBefore(button, this.diskImportButton.parentNode);
		button.outerHTML =
			"<button class='checkable menuitem diskimage'" +
			         "value='" + name + "'" +
			         "onclick='emulator.chooseDisk(this.value);'>" +
				name +
			"</button>";
	};

	$proto.selectItem = function(menuButton, kind, value /* optional */) {
		if (value === undefined) value = kind;
		var foundItem = null;
		var popup = menuButton.parentNode.querySelector(".popup");
		var options = popup.querySelectorAll(".menuitem." + kind);
		for (var i = 0; i < options.length; ++i) {
			if (options[i].value === value) {
				options[i].classList.add("checked");
				foundItem = options[i];
			} else {
				options[i].classList.remove("checked");
			}
		}
		return foundItem;
	};

	$proto.toggleTransferPopup = function(menuButton) {
		// Fill in the textbox with the contents of the clipboard, but only if
		// it's a whitespace delimited list of valid file names.
		var input = this.clipboardInput.value;
		var fileNames = [];
		var currentName = "";
		for (var i = 0; i < input.length; ++i) {
			if (/[0-9.]/.test(input[i]) && currentName.length ||
			    /[a-zA-Z]/.test(input[i])) {
				currentName += input[i];
			} else if (/\s/.test(input[i])) {
				if (currentName !== "") {
					fileNames.push(currentName);
					currentName = "";
				}
			} else {
				fileNames = [];
				currentName = "";
				break;
			}
		}
		if (currentName !== "") {
			fileNames.push(currentName);
		}
		if (fileNames.length) {
			this.linkNameInput.value = fileNames.join(" ");
		}
		this.togglePopup(menuButton);
		this.linkNameInput.focus();
	};

	$proto.togglePopup = function(menuButton) {
		if (menuButton === this.systemButton && window.event.shiftKey && !menuButton.classList.contains("feedback")) {
			this.systemButton.value = prompt("Image name", this.systemButton.value);
			return;
		}
		var popup = menuButton.parentNode.querySelector(".popup");
		if (!popup.classList.contains("open")) {
			this.closeOpenPopups();
			var items = popup.querySelectorAll(".menuitem");
			var baselineWidth = parseInt(this.controlBarBox.style.width) / 5;
			var width = Math.max(menuButton.offsetWidth, baselineWidth | 0);
			if (!popup.style.width) {
				for (var i = 0; i < items.length; ++i) {
					var itemWidth = 0;
					var kids = items[i].childNodes;
					for (var j = 0; j < kids.length; ++j) {
						if (kids[j].offsetWidth !== undefined) {
							itemWidth += kids[j].offsetWidth;
						}
					}
					width = Math.max(width, itemWidth);
				}
				// NB: Assumes no margins.
				popup.style.width = (width + 1) + "px";
			}
		}
		popup.classList.toggle("open");
	};

	$proto.closeOpenPopups = function() {
		var openPopups =
			this.controlBarBox.querySelectorAll(".menu .popup.open");
		for (var i = 0; i < openPopups.length; ++i) {
			var menu = openPopups[i].parentNode;
			this.togglePopup(menu.querySelector(".menubutton"));
		}
	};

	$proto.selectMouseButton = function(event) {
		this.closeOpenPopups();
		var clicked = event.target;
		if (event.type === "mousedown") {
			event.preventDefault();
			this.clickLeft.className = "mousebtn";
			this.clickMiddle.className = "mousebtn";
			this.clickRight.className = "mousebtn";

			clicked.classList.add("active");

			this.emulator.activeButton = parseInt(clicked.name);
			this.emulator.interclickButton = 0;
		}
		else {
			if (parseInt(clicked.name) === this.emulator.activeButton) return;
			this.emulator.interclickButton = parseInt(clicked.name);
			clicked.classList.add("interclick");
		}
	};

	$proto.toggleEmulation = function(event) {
		this.emulator.inputEmulation = this.emulCheckbox.checked;
		if(this.emulator.inputEmulation) {
			this.buttonBox.style.display="";
			if (this.buttonBox.style.position == "absolute") {
				this.leds[0].parentNode.style.marginRight = (this.buttonBox.offsetWidth + 4) + "px";
			}
		} else {
			this.buttonBox.style.display="none";
			if (this.buttonBox.style.position == "absolute") {
				this.leds[0].parentNode.style.marginRight = "";
			}
		}
	}

	$proto.setLEDState = function(ledNumber, isOn) {
		this.leds[ledNumber].classList.toggle("lit", isOn);
	};

	$proto.toggleClipboard = function() {
		this.emulator.clipboard.inputUsed = true;
		this.clipboardInput.classList.toggle("open");
		this.selectItem(this.settingsButton, "clipboard");
		this.clipboardToggle.classList.toggle("checked");
	};

	$proto.setModified = function(mod) {
		this.modifyIndicator.style.display = mod ? "inline" : "";
	};

	$proto.exportCustomImage = function() {
		var canvas = document.createElement("canvas");
		canvas.width = 1024;
		canvas.height = emulator.disk.length + 1;
		var context = canvas.getContext("2d");
		var imageData = context.createImageData(canvas.width, canvas.height);
		for (var i = 0; i < canvas.height; i++) {
			var sector = i == 0 ? emulator.rom : emulator.disk[i-1];
			for (var j = 0; j < 256; j++) {
				var b = i * 4096 + j * 16 + 2;
				imageData.data[b + 0] = sector[j] & 0xFF;
				imageData.data[b + 4] = (sector[j] >> 8) & 0xFF;
				imageData.data[b + 8] = (sector[j] >> 16) & 0xFF;
				imageData.data[b + 12] = (sector[j] >> 24) & 0xFF;
				imageData.data[b + 1] = imageData.data[b + 5] = imageData.data[b + 9] = imageData.data[b + 13] = 0xFF;
			}
		}
		context.putImageData(imageData, 0, 0);
		var that = this;
		canvas.toBlob(function(blob) {
			that.setModified(false);
			that.emulator.save(that.systemButton.value + ".png", [ blob ]);
		}, "image/png");
	}

	$proto.enableSerialLink = function(menuButton) {
		var url = window.location.href.replace(/&serialpreview=(left|right),/, "&serialpreview=");
		if (url.indexOf("?image=") != -1 && url.indexOf("&serialimage=") != -1)
			url = url.replace("?image=", "?oldimage=").replace("&serialimage=", "&image=");
		var child = window.open(url);
		this.controlBarBox.classList.add('seriallink');
		setTimeout(function() {
			var fifo1 = new SerialFIFO(window);
			var fifo2 = new child.SerialFIFO(child);
			if (this.emulator.link instanceof FileLink) {
				this.emulator.link = new SerialLink(fifo1, fifo2);
				this.emulator.filelink = null;
				child.emulator.link = new child.SerialLink(fifo2, fifo1);
				child.emulator.filelink = null;
			} else if (this.emulator.link instanceof DualLink) {
				this.emulator.link.link1 = new SerialLink(fifo1, fifo2);
				child.emulator.link.link1 = new child.SerialLink(fifo2, fifo1);
			}
			if (url.indexOf("&serialpreview=") != -1) {
				this.emulator.screenUpdater.previewcontext = child.emulator.previewcontext;
				child.emulator.screenUpdater.previewcontext = this.emulator.previewcontext;
			}
			child.emulator.ui.controlBarBox.classList.add('seriallink');
		}, 500);
	}

	// DOM Event handling

	$proto.handleEvent = function(event) {
		if (event.type !== "keypress") throw new Error(
			"Unexpected event: " + event.type
		);
		if (event.keyCode === 13) this.linkExportButton.click();
	};
})();

function ScreenUpdater(canvas, width, height) {
	this.canvas = canvas;
	this.context = canvas.getContext("2d");
	this.backBuffer = this.context.createImageData(width, height);

	this.clear();
}

(function(){
	var $proto = ScreenUpdater.prototype;

	$proto.backBuffer = null;
	$proto.canvas = null;
	$proto.context = null;
	$proto.previewcontext = null;
	$proto.maxX = null;
	$proto.maxY = null;
	$proto.minX = null;
	$proto.minY = null;
	$proto.update = null;

	ScreenUpdater.paint = $proto.paint = function(updater) {
		updater.context.putImageData(
			updater.backBuffer, 0, 0, updater.minX, updater.minY,
			updater.maxX - updater.minX + 1, updater.maxY - updater.minY + 1
		);
		updater.clear();
		if (updater.previewcontext != null) {
			updater.previewcontext.drawImage(updater.canvas, 0, 0);
		}
	};

	$proto.mark = function(x, y) {
		if (x < this.minX) this.minX = x;
		if (y < this.minY) this.minY = y;
		if (x + 31 > this.maxX) this.maxX = x + 31;
		if (y > this.maxY) this.maxY = y;
		if (!this.update) this.update = window.setTimeout(this.paint, 1, this);
	};

	$proto.clear = function() {
		this.minX = this.minY = 4096;
		this.maxX = this.maxY = 0;
		this.update = null;
	};
})();

function Clipboard(widget) {
	this._input = widget;
}

(function(){
	var $proto = Clipboard.prototype;

	$proto._buffer = null;
	$proto._input = null;
	$proto._count = null;
	$proto.inputUsed = false;

	$proto.getSize = function() {
		// assert(this._buffer === null)
		// assert(this._count === null)
		if (!this.inputUsed && navigator.clipboard && navigator.clipboard.readText) {
			var that = this;
			navigator.clipboard.readText().then(function(txt) {
				that._input.value = txt;
				that._buffer = that._input.value.split("\n").join("\r").split("");
				emulator.machine.repeatLastLoad(that._buffer.length);
				emulator.machine.setStall(false);
				emulator.reschedule();
			}, function(err) {
				emulator.machine.setStall(false);
				emulator.reschedule();
			});
			that._buffer = [];
			emulator.machine.setStall(true);
			return 0;
		}
		this._buffer = this._input.value.split("\n").join("\r").split("");
		return this._buffer.length;
	};

	$proto.expect = function(count) {
		// assert(this._buffer === null)
		// assert(this._count === null)
		this._buffer = [];
		this._count = count;
	};

	$proto.putData = function(charBits) {
		// assert(this._buffer !== null)
		// assert(this._count > 0)
		this._buffer.push(String.fromCharCode(charBits));
		this._count--;
		if (this._count === 0) {
			this._input.value = this._buffer.join("").split("\r").join("\n");
			this._buffer = null;
			this._count = null;
			if (!this.inputUsed && navigator.clipboard && navigator.clipboard.writeText) {
				navigator.clipboard.writeText(this._input.value);
			}
		}
	};

	$proto.getData = function() {
		// assert(this._buffer.length > 0)
		var singleChar = this._buffer.shift();
		if (this._buffer.length === 0) this._buffer = null;
		// XXX Warn for non-ASCII?
		return singleChar.charCodeAt(0) | 0;
	};
})();

// TODO: Avoid anything exotic for virtualized keys.
//
// For example, Macs don't typically make the F keys easily discoverable.
// They also don't have a real delete key.  Chromebooks neither have a delete
// key nor do they have F11 and F12 keys.  The F keys that do exist are worse
// than undiscoverable--some of theme aren't even usable as F keys unless you
// get rid of ChromeOS.  So in making our choices to virualize things that may
// be difficult or impossible to access (such as middle click, interclicks) we
// need to make sure avoid all of these, otherwise we're offering one
// impossibility as an alternative to another impossibility.
//
// What seems to be relatively safe are Ctrl, Alt, and Shift.  I'm not sure
// that Esc is safe on the MacBook Touchbar.  However, presently we're already
// using Alt to emulate middle click, its limitations have made themselves
// known, given that we're targeting the Web.  However, the emulators also use
// Alt to emulate middle click.  This is prone to error, given that Alt+Left
// and Alt+Right are popular shortcuts for back and forwards, and Firefox
// shows the menubar when tapping Alt.  When switching desktops using a
// keyboard shortcut that involves Alt, releasing the Alt key after landing on
// the desired desktop still fires if that desktop contains an focused window
// running our emulator.  So Alt should be regarded as partially safe:
// keyboard shortcuts involving Alt (including bare Alt), but mouse clicks
// with Alt modifiers should be okay.
//
// We also need to be sensitive to platform conventions.  We aren't right now,
// but we should be doing something like:
//
//   * Alt+Click -> right
//   * Ctrl+Click (non-mac) -> middle
//   * Ctrl+Click (Mac) -> right
//   * Cmd+Click (Mac) -> middle
//   * Cmd+Ctrl+Click (Mac) -> right
//   * Cmd+Alt+Click (Mac) -> right
//
// (The latter two are said to be the typical way that applications simulate
// non-Mac Ctrl+click in Mac apps, but this hasn't been verified.)
function VirtualKeyboard(screen, emulator) {
	// Reading keyboard input on the Web is still a big mess.
	var KEY_CODES_TO_PREVENT_DEFAULT = ["AltLeft", "AltRight", "ArrowDown", "ArrowLeft", "ArrowRight", "ArrowUp",
		"Backspace", "Delete", "End", "Enter", "Escape", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10",
		"F11", "F12", "F13", "F14", "F15", "F16", "F17", "F18", "F19", "F20", "F21", "F22", "F23", "F24", "F25",
		"F26", "F27", "F28", "F29", "Help", "Home", "Insert", "NumpadEnter", "PageDown", "PageUp", "Space", "Tab"];
	screen.addEventListener("keydown", function(event) {
		var code = event.keyCode;
		// Alt
		if (emulator.inputEmulation && code === 18 && !event.ctrlKey && event.key != "AltGraph") {
			event.preventDefault();
			emulator.registerMouseButton(2, true);
			return;
		}
		// JS Keyboard
		if (emulator.registerRawEvent(3, event)) {
			if (KEY_CODES_TO_PREVENT_DEFAULT.indexOf(event.code) != -1 || event.ctrlKey || event.altKey || event.metaKey) {
				event.preventDefault();
			}
			return;
		}
		// Backspace, Tab, Enter, or Escape
		if (code === 8 || code === 9 || code === 13 || code == 27) {
			event.preventDefault();
			emulator.registerKey(code);
			return;
		}
		// Insert or F1
		if (code === 45 || code === 112) {
			event.preventDefault();
			emulator.registerKey(26);
			return;
		}
		// Delete
		if (code == 46) {
			emulator.registerKey(127);
			return;
		}
		// Cursor keys
		if (code >= 37 && code <= 40) {
			event.preventDefault();
			emulator.registerKey(code == 38 || code == 39 ? 57 - code : code - 20);
			return;
		}
	});
	screen.addEventListener("keyup", function(event) {
		// Alt
		if (emulator.inputEmulation && event.keyCode === 18 && !event.ctrlKey && event.key != "AltGraph") {
			event.preventDefault();
			emulator.registerMouseButton(2, false);
			return;
		}
		emulator.registerRawEvent(2, event);
	});
	screen.addEventListener("keypress", function(event) {
		if (emulator.registerRawEvent(1, event)) {
			return;
		}
		var code = event.keyCode;
		// Backspace, Tab, Enter, or Escape
		if (code === 8 || code === 9 || code === 13 || code == 27) {
			event.preventDefault();
			emulator.registerKey(code);
			return;
		}
		// Insert or F1
		if (event.charCode === 0 && (code === 45 || code === 112)) {
			event.preventDefault();
			emulator.registerKey(26);
			return;
		}
		// printable char
		if (event.charCode !== 0) {
			event.preventDefault();
			emulator.registerKey(event.charCode | 0);
			return;
		}
	});
	screen.addEventListener("blur", function(event) {
		emulator.registerMouseButton(1, false);
		emulator.registerMouseButton(2, false);
		emulator.registerMouseButton(3, false);
	});
}

function DualLink(link1, link2) {
	this.link1 = link1;
	this.link2 = link2;
	this.link = 1;
}

(function(){
	var $proto = DualLink.prototype;

	$proto.getStatus = function() {
		return this.link1.getStatus() | (this.link2.getStatus() << 2);
	};

	$proto.getData = function() {
		return this["link" + this.link].getData();
	};

	$proto.setStatus = function(val) {
		this.link = (val == 1) ? 2 : 1;
	};

	$proto.setData = function(val) {
		this["link" + this.link].setData(val);
	};
})();

function SerialFIFO(notifyWindow) {
	this.notifyWindow = notifyWindow;
	this.buffer = new Uint8Array(4096);
	this.readPtr = 0;
	this.writePtr = 0;
}

(function(){
	var $proto = SerialFIFO.prototype;

	$proto.readReady = function() {
		return this.readPtr != this.writePtr;
	};

	$proto.read = function() {
		var result = this.buffer[this.readPtr];
		this.readPtr = (this.readPtr + 1) % 4096;
		return result;
	};

	$proto.writeReady = function() {
		return (this.writePtr + 1) % 4096 != this.readPtr;
	};

	$proto.write = function(val) {
		this.buffer[this.writePtr] = val;
		this.writePtr = (this.writePtr + 1) % 4096;
		var thatNotifyWindow = this.notifyWindow;
		thatNotifyWindow.setTimeout(function() {
			thatNotifyWindow.emulator.machine.resetWaitMillis();
			thatNotifyWindow.emulator.reschedule();
		}, 1);
	};
})();


function SerialLink(inFIFO, outFIFO) {
	this.inFIFO = inFIFO;
	this.outFIFO = outFIFO;
}

(function(){
	var $proto = SerialLink.prototype;

	SerialLink.TX_READY = $proto.TX_READY = 0x01;
	SerialLink.RX_READY = $proto.RX_READY = 0x02;

	$proto.getStatus = function() {
		var result = 0;
		if (this.outFIFO.writeReady())
			result |= this.RX_READY;
		if (this.inFIFO.readReady())
			result |= this.TX_READY;
		return result;
	};

	$proto.getData = function() {
		return this.inFIFO.readReady() ? this.inFIFO.read() : 0;
	};

	$proto.setStatus = function(val) {};

	$proto.setData = function(val) {
		if (this.outFIFO.writeReady())
			this.outFIFO.write(val);
	};
})();

function FileLink(emulator) {
	this.emulator = emulator;
	this.pending = [];
}

(function(){
	var $proto = FileLink.prototype;

	FileLink.SUPPLY_TRANSFER = $proto.SUPPLY_TRANSFER = 0x21;
	FileLink.DEMAND_TRANSFER = $proto.DEMAND_TRANSFER = 0x22;
	FileLink.GLOB_TRANSFER = $proto.GLOB_TRANSFER = 0x23;

	FileLink.TX_READY = $proto.TX_READY = 0x01;
	FileLink.RX_READY = $proto.RX_READY = 0x02;

	FileLink.ACK = $proto.ACK = 0x10;
	FileLink.NAK = $proto.NAK = 0x11;

	$proto.transfer = null;

	$proto.queue = function(transfer) {
		if (this.transfer) {
			this.pending.push(transfer);
		} else {
			this.transfer = transfer;
		}
	};

	$proto.getStatus = function() {
		var result = this.RX_READY;
		if (this.transfer !== null) {
			if (this.transfer.readyState === 0) {
				throw new Error("Unexpected transfer state: 0 (dead)");
			} else if (this.transfer.readyState !== 1) {
				result |= this.TX_READY;
			}
		}
		return result;
	};

	$proto.getData = function() {
		var result = 0;
		var count = this.transfer.count;
		var fileName = this.transfer.fileName;
		if (count === 0) {
			result = this.transfer.type;
		} else if (count - 1 < fileName.length) {
			result = fileName.charCodeAt(count - 1);
		} else if (count - 1 === fileName.length) {
			result = 0; // ASCII NUL
		} else {
			result = this.transfer.getPacketByte();
		}

		++this.transfer.count;
		this._checkFinished(this.transfer);
		return result;
	};

	$proto.setStatus = function(val) {};

	$proto.setData = function(val) {
		this.transfer.acceptLinkByte(val);
		this._checkFinished(this.transfer);
	};

	$proto._checkFinished = function(transfer) {
		if (transfer.readyState === 0) {
			delete this.transfer;
			this.emulator.completeTransfer(transfer);
			if (this.pending.length) {
				this.transfer = this.pending.shift();
			}
		}
	};

})();

function SupplyTransfer(file) {
	// See `acceptLinkByte` for the significance of `readyState` transitions.
	this.readyState = 1;
	this.offset = 0;
	this.count = 0;
	this.type = FileLink.SUPPLY_TRANSFER;
	this.fileName = file.name;

	var reader = new FileReader();
	reader.addEventListener("loadend", this);
	reader.readAsArrayBuffer(file);
}

(function(){
	var $proto = SupplyTransfer.prototype;

	$proto.success = 0;

	$proto.handleEvent = function(event) {
		if (event.type !== "loadend") throw new Error(
			"Unexpected event: " + event.type
		);

		var result = event.target.result;
		if (/\.txt$/.test(this.fileName)) {
			this.fileName = this.fileName.substring(0, this.fileName.length - 4);
			var text = String.fromCharCode.apply(null, new Uint8Array(event.target.result)).split(/\n|\r\n?/).join("\r");
			result = new ArrayBuffer(text.length);
			new Uint8Array(result).set(text.split('').map(function(x) {return x.charCodeAt();}));
		}
		this.fileBytes = new DataView(result);
		this.readyState = 2;
	};

	$proto.getPacketByte = function() {
		var sent = this.count - 1 - (this.fileName.length + 1);
		if ((sent % 256) === 0) {
			var remaining = this.fileBytes.byteLength - this.offset;
			if (remaining >= 255) {
				result = 255;
			} else {
				result = remaining;
				this.readyState = -2;
			}
		} else {
			result = this.fileBytes.getUint8(this.offset);
			++this.offset;
		}
		return result;
	};

	/**
	 * readyState-based flow control:
	 *   -2: Expect 2 ACKs after current packet, then die
	 *   -1: Expect 1 more ACK, then die
	 *    0: Dead
	 *    1: Start state/receive-only
	 *   >1: Ready to transmit
	 *
	 * NB: Technically readyState < 0 is also treated as "ready to transmit",
	 * because it doesn't necessarily mean that an ACK is eminently expected,
	 * only that we know how many ACKs are coming; this packet will contain EOF.
	 */
	$proto.acceptLinkByte = function(val) {
		if (val === FileLink.NAK) {
			this.success = FileLink.NAK;
			this.readyState = 0;
		} else {
			if (val !== FileLink.ACK) throw new Error("Expected ACK");
			if (this.readyState < 0) ++this.readyState;
		}
	};
})();

function DemandTransfer(name) {
	this.blocks = [];
	this.offset = 0;
	this.count = 0;
	this.type = FileLink.DEMAND_TRANSFER;
	this.fileName = name;
	// [readyState: 1] is reserved for transfers' start state, but we're ready
	// to transmit immediately, so we skip straight ahead to [readyState: 2].
	this.readyState = 2;
}

(function(){
	var $proto = DemandTransfer.prototype;

	$proto.success = 0;

	/**
	 * readyState-based flow control:
	 *   -1: Expect 1 (final) ACK is needed from us, then die
	 *    0: Dead
	 *    1: Start state/receive-only (unused)
	 *    2: Ready for ACK
	 *    3: Ready for block size
	 *    4: Ready for block byte
	 */
	$proto.acceptLinkByte = function(val) {
		switch (this.readyState) {
			case 2:
				if (val === FileLink.NAK) {
					this.success = FileLink.NAK;
					this.readyState = 0;
				} else if (val === FileLink.ACK) {
					this.readyState = 3;
				} else {
					throw new Error("Expected ACK");
				}
			break;
			case 3:
				if (val === 0) {
					this.readyState = -1;
				} else {
					this.blocks.push(new Uint8Array(val));
					this.readyState = 4;
				}
			break;
			case 4:
				topBlock = this.blocks[this.blocks.length - 1];
				topBlock[this.offset] = val;
				++this.offset;
				if (this.offset === topBlock.byteLength) {
					if (this.offset < 255) {
						this.readyState = -1;
					} else {
						this.readyState = 3;
					}
					this.offset = 0;
				}
			break;
			default: throw new Error("Unexpected state: " + this.readyState);
		}
	};

	$proto.getPacketByte = function() {
		if (this.readyState < 0 || this.readyState >= 2) {
			result = FileLink.ACK;
			if (this.readyState < 0) {
				++this.readyState;
			}
		} else {
			throw new Error("Unexpected byte request");
		}
		return result;
	};
})();

function GlobTransfer(link, name) {
	this._link = link;
	this.type = FileLink.GLOB_TRANSFER;
	this.count = 0;
	this.fileName = name;
	this.readyState = 2;
	this.currentName = "";
}

(function(){
	var $proto = GlobTransfer.prototype;

	$proto.success = 0;

	/**
	 * readyState-based flow control:
	 *   -1: Expect 1 (final) ACK is needed from us, then die
	 *    0: Dead
	 *    1: Start state/receive-only (unused)
	 *    2: Ready for ACK
	 *    3: Ready for file names
	 */
	$proto.acceptLinkByte = function(val) {
		switch (this.readyState) {
			case 2:
				if (val === FileLink.NAK) {
					this.success = FileLink.NAK;
					this.readyState = 0;
				} else if (val === FileLink.ACK) {
					this.readyState = 3;
				} else {
					throw new Error("Expected ACK");
				}
			break;
			case 3:
				if (val === 0 && this.currentName.length === 0) {
					this.readyState = -1;
				} else if (val === 0) {
					this._link.queue(new DemandTransfer(this.currentName));
					this.currentName = "";
				} else {
					this.currentName += String.fromCharCode(val);
				}
			break;
			default: throw new Error("Unexpected state: " + this.readyState);
		}
	};

	$proto.getPacketByte = function() {
		if (this.readyState < 0 || this.readyState >= 2) {
			result = FileLink.ACK;
			if (this.readyState < 0) {
				++this.readyState;
			}
		} else {
			throw new Error("Unexpected byte request");
		}
		return result;
	};
})(); //

function SiteConfigLoader(emulator) {
	this.emulator = emulator;
}

(function(){
	var $proto = SiteConfigLoader.prototype;

	SiteConfigLoader.read = $proto.read = function(uri, emulator) {
		var loader = new SiteConfigLoader(emulator);
		var request = new XMLHttpRequest();
		request.addEventListener("load", loader);
		request.open("GET", uri);
		request.send(null);
	};

	$proto.handleEvent = function(event) {
		if (event.type !== "load") throw new Error(
			"Unexpected event: " + event.type
		);

		var config = JSON.parse(event.target.responseText);
		var reader = new ROMFileReader(config.rom, config);
		return void(reader.prepareContentsThenNotify(this.emulator));
	};
})();

function PNGImageReader(name) {
	this.name = name;
}

(function(){
	var $proto = PNGImageReader.prototype;

	$proto.prepareContentsThenNotify = function(listener) {
		this.listener = listener;
		this.container = new Image();
		this.container.addEventListener("load", this);
		this.container.src = this.name + ".png";
	};

	$proto.handleEvent = function(event) {
		if (event.type !== "load") throw new Error(
			"Unexpected event: " + event.type
		);

		var canvas = document.createElement("canvas");
		var width = canvas.width = this.container.width;
		var height = canvas.height = this.container.height;
		var context = canvas.getContext("2d");

		context.drawImage(this.container, 0, 0);
		var data = context.getImageData(0, 0, width, height).data;
		var contents = this._unpack(data, width, height);
		this.listener.useSystemImage(this.name, contents);
	};

	$proto._unpack = function(imageData, width, height) {
		var contents = [];
		// skip boot ROM
		for (var i = 1; i < height; i++) {
			var sectorWords = new Int32Array(width / 4);
			for (var j = 0; j < width / 4; j++) {
				var b = i * 4096 + j * 16 + 2;
				sectorWords[j] =
					((imageData[b +  0] & 0xFF) <<  0) |
					((imageData[b +  4] & 0xFF) <<  8) |
					((imageData[b +  8] & 0xFF) << 16) |
					((imageData[b + 12] & 0xFF) << 24) |
					0;
			}
			contents[i-1] = sectorWords;
		}
		return contents;
	};
})();

function DiskFileReader(file) {
	this.file = file;
}

(function(){
	var $proto = DiskFileReader.prototype;

	DiskFileReader.getContents = $proto.getContents = function(buffer) {
		var contents = [];
		var sectorStart = 0;
		var view = new DataView(buffer);
		while (sectorStart < view.byteLength) {
			var sectorWords = new Int32Array(1024 / 4);
			for (var i = 0; i < 1024 / 4; i++) {
				sectorWords[i] = view.getInt32(sectorStart + i * 4, true);
			}
			contents.push(sectorWords);
			sectorStart += 1024;
		}
		return contents;
	};

	$proto.prepareContentsThenNotify = function(listener) {
		this.listener = listener;
		var reader = new FileReader();
		reader.addEventListener("loadend", this);
		reader.readAsArrayBuffer(this.file);
	};

	$proto.handleEvent = function(event) {
		if (event.type !== "loadend") throw new Error(
			"Unexpected event: " + event.type
		);

		var contents = this.getContents(event.target.result);
		this.listener.useSystemImage(this.file.name, contents);
	};
})();

function ROMFileReader(uri, config) {
	this.uri = uri;
	this.config = config;
}

(function(){
	var $proto = ROMFileReader.prototype;

	$proto.prepareContentsThenNotify = function(listener) {
		this.listener = listener;
		var request = new XMLHttpRequest();
		request.addEventListener("load", this);
		request.open("GET", this.uri);
		request.responseType = "arraybuffer";
		request.send(null);
	};

	$proto.handleEvent = function(event) {
		if (event.type !== "load") throw new Error(
			"Unexpected event: " + event.type
		);
		var rom = DiskFileReader.getContents(event.target.response);
		this.listener.useConfiguration(this.config, rom[0]);
	};
})();
