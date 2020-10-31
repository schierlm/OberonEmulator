function WizNet(emulator) {
	this.emulator = emulator;
	this.netData = {
		tcpSockets: [],
		udpSockets: [],
		listenSockets: []
	};
}

(function(){
	var $proto = WizNet.prototype;

	$proto.netCommand = function(value, memory) {
		var emulator = this.emulator;
		var netData = this.netData;
		var insn = memory[value / 4];
		if (insn >> 16 != 1)
			return;

		var offset = value / 4;

		var getNameString = function(ram, offset) {
			var bytes = new Uint8Array(128);
			new Uint32Array(bytes.buffer).set(ram.subarray(offset, offset+32));
			return String.fromCharCode.apply(null, bytes).replace(/\0.*/g,"");
		};

		var setData = function(ram, offset, bytes, maxLen) {
			if (bytes.length > maxLen || bytes.length % 4 != 0) {
				var oldBytes = bytes;
				bytes = new Uint8Array(Math.min(((bytes.length + 3) / 4 | 0) * 4, maxLen));
				bytes.set(oldBytes);
			}
			if (bytes.length % 4 != 0)
				debugger;
			ram.subarray(offset, offset + bytes.length / 4).set(new Uint32Array(bytes.buffer));
		};

		var setNameString = function(ram, offset, value) {
			var bytes = Uint8Array.from(value+"\0", function(c) { return c.charCodeAt(0)});
			setData(ram, offset, bytes, 128);
		};

		var allocId = function(array) {
			for(var i=0; i<array.length; i++) {
				if (array[i] == null)
					return i;
			}
			return array.push(null) - 1;
		}

		var eventCallback = function(event) {
			var json = JSON.parse(event.target.responseText);
			for(var i = 1; i < json.ints.length; i++) {
				memory[offset + i] = json.ints[i];
			}
			if (json.so && json.str) {
				setNameString(memory, offset + json.so, json.str);
			}
			emulator.machine.setStall(false);
			emulator.reschedule();
		};

		var openTcpSocket = function(params) {
			var ws = new WebSocket(window.offlineInfo.netConfig.TCP+"?"+params);
			ws.binaryType = "arraybuffer";
			var sockinfo = {
				closed: false,
				bufLen: 0,
				buf: [],
				socket: ws
			};
			ws.onmessage = function(event) {
				sockinfo.bufLen += event.data.byteLength;
				sockinfo.buf.push(new Uint8Array(new Uint8Array(event.data)));
			};
			ws.onclose = function(event) {
				sockinfo.closed = true;
			};
			return sockinfo;
		}

		switch (insn) {
			case 0x10001: { // IP.StrToAdr + DNS.HostByName
				var host = getNameString(memory, offset + 3);
				var request = new XMLHttpRequest();
				request.addEventListener("load", eventCallback);
				request.open("GET", window.offlineInfo.netConfig.HostByName+"?v="+encodeURIComponent(host));
				request.send(null);
				emulator.machine.setStall(true);
				break;
			}
			case 0x10002: { // IP.AdrToStr
				var adr = memory[offset + 2];
				setNameString(memory, offset + 3, ((adr >> 24) & 0xFF) + "." + ((adr >> 16) & 0xFF) + "." + ((adr >> 8) & 0xFF) + "." + (adr & 0xFF));
				memory[offset + 1] = 0;
				break;
			}
			case 0x10003: { // DNS.HostByNumber
				var adr = memory[offset + 2];
				var request = new XMLHttpRequest();
				request.addEventListener("load", eventCallback);
				request.open("GET", window.offlineInfo.netConfig.HostByNumber+"?v="+encodeURIComponent(adr));
				request.send(null);
				emulator.machine.setStall(true);
				break;
			}
			case 0x10004: { // UDP.Open
				var lport = memory[offset + 3];
				var socketid = allocId(netData.udpSockets);
				memory[offset + 2] = socketid;
				var ws = new WebSocket(window.offlineInfo.netConfig.UDP+"?port="+lport);
				ws.binaryType = "arraybuffer";
				var sockinfo = {
					stalling: true,
					closed: false,
					packets: [],
					socket: ws
				};
				netData.udpSockets[socketid] = sockinfo;
				ws.onmessage = function(event) {
					if (typeof event.data === "string") {
						memory[offset + 1] = 0;
						memory[offset + 3] = +event.data;
						sockinfo.stalling = false;
						emulator.machine.setStall(false);
						emulator.reschedule();
					} else {
						var data = new Uint8Array(event.data.byteLength-6);
						data.set(new Uint8Array(event.data).subarray(6));
						var dv = new DataView(event.data);
						var packet = {
							host: dv.getUint32(0, false),
							port: dv.getUint16(4, false),
							data: data
						};
						sockinfo.packets.push(packet);
					}
				};
				ws.onclose = function(event) {
					if (sockinfo.stalling) {
						memory[offset + 1] = 9999;
						sockinfo.stalling = false;
						emulator.machine.setStall(false);
						emulator.reschedule();
					}
					sockinfo.closed = true;
				};
				emulator.machine.setStall(true);
				break;
			}
			case 0x10005: { // UDP.Close
				var socketid = memory[offset + 2];
				if (socketid >= 0 && socketid < netData.udpSockets.length && netData.udpSockets[socketid]) {
					netData.udpSockets[socketid].socket.close();
					netData.udpSockets[socketid] = null;
					memory[offset + 1] = 0;
				} else {
					memory[offset + 1] = 3505;
				}
				break;
			}
			case 0x10006: { // UDP.Send
				var socketid = memory[offset + 2], len = memory[offset + 5];
				if (socketid >= 0 && socketid <  netData.udpSockets.length && netData.udpSockets[socketid]) {
					var wordlen = (len + 3) / 4 | 0;
					var buf = new Uint8Array(6 + wordlen * 4);
					new Uint32Array(buf.buffer, 0, wordlen).set(memory.subarray(offset + 6, offset + 6 + wordlen));
					buf.subarray(6).set(buf.subarray(0, wordlen*4));
					var dv = new DataView(buf.buffer);
					dv.setUint32(0, memory[offset+3], false);
					dv.setUint16(4, memory[offset+4], false);
					try {
						netData.udpSockets[socketid].socket.send(buf.buffer);
						memory[offset + 1] = 0;
					} catch (e){
						memory[offset + 1] = 9999;
					}
				} else {
					memory[offset + 1] = 3505;
				}
				break;
			}
			case 0x10007: { // UDP.Receive
				var socketid = memory[offset + 2];
				var len = memory[offset + 5];
				if (socketid >= 0 && socketid < netData.udpSockets.length && netData.udpSockets[socketid]) {
					var ds = netData.udpSockets[socketid];
					var buf = new Uint8Array(len);
					var doReceive = function(packet) {
						if (packet == null) {
							memory[offset + 1] = 3704;
							memory[offset + 5] = 0;
						} else {
							memory[offset + 1] = 0;
							memory[offset + 3] = packet.host
							memory[offset + 4] = packet.port
							memory[offset + 5] = packet.data.length
							setData(memory, offset + 7, packet.data, 1500);
						}
					};
					var packet = ds.packets.shift();
					if (packet == null) {
						emulator.machine.setStall(true);
						setTimeout(function() {
							var packet = ds.packets.shift();
							doReceive(packet);
							emulator.machine.setStall(false);
							emulator.reschedule();
						}, Math.max(memory[offset + 6], 1));
					} else {
						doReceive(packet);
					}
					break;
				} else {
					memory[offset + 1] = 3505;
					memory[offset + 5] = 0;
				}
				break;
			}
			case 0x10008: { // TCP.Open
				var lport = memory[offset + 3];
				var fport = memory[offset + 5];
				if (memory[offset + 4] == 0 && fport == 0) { // listen
					var socketid = allocId(netData.listenSockets);
					memory[offset + 2] = -socketid - 1;
					var ws = new WebSocket(window.offlineInfo.netConfig.Listen+"?port="+lport);
					var sockinfo = {
						closed: false,
						pendingSockInfos: [],
						socket: ws
					};
					netData.listenSockets[socketid] = sockinfo;
					ws.onmessage = function(event) {
						var edata = event.data.split(",");
						var si = openTcpSocket("id="+(+edata[0]));
						si.socket.onopen = function() {
							sockinfo.pendingSockInfos.push({
								sockinfo: si,
								raddr: +edata[1],
								rport: +edata[2]
							});
						};
					};
					ws.onclose = function(event) {
						sockinfo.closed = true;
					}
					memory[offset + 1] = 0;
				} else { // connect
					var socketid = allocId(netData.tcpSockets);
					memory[offset + 2] = socketid;
					netData.tcpSockets[socketid] = openTcpSocket("host="+memory[offset + 4]+"&lport="+lport+"&fport="+fport);
					netData.tcpSockets[socketid].socket.onopen = function() {
						emulator.machine.setStall(false);
						emulator.reschedule();
					}
					memory[offset + 1] = 0;
					emulator.machine.setStall(true);
				}
				break;
			}
			case 0x10009: { // TCP.SendChunk
				var socketid = memory[offset + 2];
				var len = memory[offset + 3];
				if (socketid >= 0 && socketid < netData.tcpSockets.length && netData.tcpSockets[socketid]) {
					var wordlen = (len + 3) / 4 | 0;
					var buf = new Uint8Array(wordlen * 4);
					new Uint32Array(buf.buffer).set(memory.subarray(offset + 5, offset + 5 + wordlen));
					try {
						netData.tcpSockets[socketid].socket.send(buf.subarray(0, len));
						memory[offset + 1] = 0;
					} catch (e) {
						memory[offset + 1] = 3702;
					}
				} else {
					memory[offset + 1] = 3706;
				}
				break;
			}
			case 0x1000A: { // TCP.ReceiveChunk
				var socketid = memory[offset + 2];
				var len = memory[offset + 3];
				var minlen = memory[offset + 4];
				if (socketid >= 0 && socketid < netData.tcpSockets.length && netData.tcpSockets[socketid]) {
					var tcpsock = netData.tcpSockets[socketid];
					if (tcpsock.bufLen == 0 && tcpsock.closed) {
						memory[offset + 1] = 3707;
						memory[offset + 3] = 0;
					} else if (tcpsock.bufLen < minlen) {
						memory[offset + 1] = 3704;
						memory[offset + 3] = 0;
					} else {
						var currLen = Math.min(len, tcpsock.bufLen);
						tcpsock.bufLen -= currLen;
						memory[offset + 1] = 0;
						memory[offset + 3] = currLen;
						var chunk = new Uint8Array(currLen);
						var chunkLen = 0;
						while(chunkLen < currLen) {
							var bufPart = tcpsock.buf.shift();
							var usedLen = Math.min(bufPart.length, currLen - chunkLen);
							chunk.subarray(chunkLen, chunkLen+usedLen).set(bufPart.subarray(0, usedLen));
							chunkLen += usedLen;
							if (usedLen != bufPart.length) {
								var rest = new Uint8Array(bufPart.length - usedLen);
								rest.set(bufPart.subarray(usedLen));
								tcpsock.buf.unshift(rest);
							}
						}
						setData(memory, offset + 5, chunk, 1500);
					}
				} else {
					memory[offset + 1] = 3706;
					memory[offset + 3] = 0;
				}
				break;
			}
			case 0x1000B: { // TCP.Available
				var socketid = memory[offset + 2];
				if (socketid >= 0 && socketid < netData.tcpSockets.length && netData.tcpSockets[socketid]) {
					var tcpsock = netData.tcpSockets[socketid];
					memory[offset + 1] = tcpsock.bufLen + (tcpsock.closed ? 1 : 0);
				} else {
					memory[offset + 1] = 0;
				}
				break;
			}
			case 0x1000C: { // TCP.Close
				var socketid = memory[offset + 2];
				if (socketid >= 0 && socketid < netData.tcpSockets.length && netData.tcpSockets[socketid]) {
					netData.tcpSockets[socketid].socket.close();
					netData.tcpSockets[socketid] =  null;
					memory[offset + 1] = 0;
				} else if (socketid < 0 && -socketid - 1 < netData.listenSockets.length && netData.listenSockets[-socketid - 1]) {
					var psis = netData.listenSockets[-socketid - 1].pendingSockInfos;
					for(var i = 0; i<psis.length; i++) {
						psis[i].sockinfo.socket.close();
					}
					netData.listenSockets[-socketid - 1].socket.close();
					netData.listenSockets[-socketid - 1] = null;
					memory[offset + 1] = 0;
				} else {
					memory[offset + 1] = 3706;
				}
				break;
			}
			case 0x1000D: { // TCP.Accept
				var socketid = -memory[offset + 2] - 1;
				if (socketid >= 0 && socketid < netData.listenSockets.length && netData.listenSockets[socketid]) {
					var clientid = allocId(netData.tcpSockets);
					memory[offset + 1] = 0;
					if (netData.listenSockets[socketid].pendingSockInfos.length > 0) {
						var psi = netData.listenSockets[socketid].pendingSockInfos.shift();
						netData.tcpSockets[clientid] = psi.sockinfo;
						memory[offset + 3] = clientid;
						memory[offset + 4] = psi.raddr;
						memory[offset + 5] = psi.rport;
					} else {
						memory[offset + 3] = -1;
					}
				} else {
					memory[offset + 1] = 3706;
					memory[offset + 3] = 0;
				}
				break;
			}
		}
	};
})();
