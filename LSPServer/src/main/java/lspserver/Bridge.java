package lspserver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import lspserver.OberonFile.AnalysisResult;
import lspserver.OberonFile.Identifier;
import lspserver.OberonFile.IdentifierReference;
import lspserver.OberonFile.ModuleDepType;
import lspserver.OberonFile.ParamTag;
import lspserver.OberonFormatter.FormatTokenInfo;

/** Bridge to the Oberon compiler / VM */
public class Bridge {

	public static final List<String> TOKEN_TYPES = Arrays.asList(
			SemanticTokenTypes.Namespace,
			SemanticTokenTypes.Type,
			SemanticTokenTypes.Struct,
			SemanticTokenTypes.Parameter,
			SemanticTokenTypes.Variable,
			SemanticTokenTypes.Property,
			SemanticTokenTypes.Function,
			SemanticTokenTypes.Keyword,
			SemanticTokenTypes.Comment,
			SemanticTokenTypes.String,
			SemanticTokenTypes.Number,
			SemanticTokenTypes.Operator);

	public static final List<String> TOKEN_MODIFIERS = Arrays.asList(
			SemanticTokenModifiers.Declaration, /* pos == here */
			SemanticTokenModifiers.Modification, /* e.g. x := 9 */
			SemanticTokenModifiers.Readonly /* constants */
	);

	private final DataOutputStream dos;
	private final DataInputStream dis;
	private final Callable<Void> shutdown;

	public Bridge(DataOutputStream dos, DataInputStream dis, Callable<Void> shutdown) {
		this.dos = dos;
		this.dis = dis;
		this.shutdown = shutdown;
	}

	/** Return whether dependent modules should be analyzed again. */
	public synchronized boolean analyze(OberonFile file, AnalysisResult ar) throws IOException {
		ar.setModuleName(null);
		ar.getModuleDeps().clear();
		ar.getErrors().clear();
		ar.setSemanticTokens(new int[0]);
		ar.getOutline().clear();
		ar.getIdDefinitions().clear();
		ar.getIdReferences().clear();
		ar.getFunctionDefinitions().clear();
		ar.getFunctionRanges().clear();
		ar.getParamTags().clear();
		ar.getExportedSymbolRefs().clear();
		String content = file.getContent();
		ar.setContentHash(OberonFile.hashText(content));
		if (content.length() == 0) {
			ar.getErrors().add(new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)), "Empty file"));
			return false;
		}
		ProtocolConstant.INST_GetModuleInfo.send(dos);
		writeIntLE(content.length());
		dos.write(content.getBytes(StandardCharsets.ISO_8859_1));
		dos.flush();
		boolean result = false, functionPending = false, importAliasPending = false;
		int prevSymbolEnd = 0, lastSymbolEnd = 0, declarationBlockStart = -1, paramDepth = 0;
		List<List<DocumentSymbol>> outlineStack = new ArrayList<>();
		List<Range> procRangeStack = new ArrayList<>();
		List<Integer> functionNamePosStack = new ArrayList<>();
		outlineStack.add(new ArrayList<>());
		SortedMap<Integer,int[]> semanticTokenInformation = new TreeMap<>();
		List<int[]> definitionLists = new ArrayList<>();
		Set<Integer> overridingPositions = new HashSet<>();
		boolean inProcParam = false;
		Identifier lastUndefinedSymbol = null, lastExport = null;
		loop: while (true) {
			ProtocolConstant res = ProtocolConstant.read(dis);
			switch (res) {
			case STATUS_OK:
				break loop;
			case STATUS_Invalid:
				throw new IOException("Invalid status received");
			case ANSWER_ModuleName:
				ar.setModuleName(readCStr());
				break;
			case ANSWER_ModuleImport:
				ar.getModuleDeps().putIfAbsent(readCStr(), new HashMap<>());
				break;
			case ANSWER_Error:
				int pos = adjustPos(readIntLE(), content.length());
				String msg = readCStr();
				ar.getErrors().add(new Diagnostic(new Range(file.getPos(pos == 0 ? 0 : pos - 1), file.getPos(pos + 1)), msg));
				break;
			case ANSWER_Warning:
				int pos_W = adjustPos(readIntLE(), content.length());
				if (pos_W == content.length())
					pos_W--;
				String msg_W = readCStr();
				Diagnostic diag_W = new Diagnostic(new Range(file.getPos(pos_W == 0 ? 0 : pos_W - 1), file.getPos(pos_W + 1)), msg_W);
				diag_W.setSeverity(DiagnosticSeverity.Warning);
				ar.getErrors().add(diag_W);
				break;
			case ANSWER_Information:
				int pos_I = adjustPos(readIntLE(), content.length());
				String msg_I = readCStr();
				Diagnostic diag_I = new Diagnostic(new Range(file.getPos(pos_I == 0 ? 0 : pos_I - 1), file.getPos(pos_I + 1)), msg_I);
				diag_I.setSeverity(DiagnosticSeverity.Information);
				ar.getErrors().add(diag_I);
				break;
			case ANSWER_SymbolFileChanged:
				result = true;
				break;
			case ANSWER_SymbolFileIndex:
				int symIdx = readIntLE();
				String symMod = readCStr();
				int symEndPos = readIntLE();
				if (ar.getExportedSymbolRefs().containsKey(symIdx)) {
					IdentifierReference ir = ar.getExportedSymbolRefs().get(symIdx);
					if (ir.getModule().equals(symMod) && ir.getEndPos() == symEndPos) {
						ar.getExportedSymbolRefs().remove(symIdx);
						Diagnostic diag_X = new Diagnostic(new Range(file.getPos(symEndPos-1), file.getPos(symEndPos)), "LSP: Symbol reported more than once");
						diag_X.setSeverity(DiagnosticSeverity.Information);
						ar.getErrors().add(diag_X);
					} else if (ir.getModule().equals(symMod)) {
						ar.getExportedSymbolRefs().remove(symIdx);
						Diagnostic diag_X = new Diagnostic(new Range(file.getPos(symEndPos-1), file.getPos(symEndPos)), "LSP: Symbol index "+symIdx+" reported at different positions: " + ir.getEndPos()+ " and "+symEndPos);
						diag_X.setSeverity(DiagnosticSeverity.Error);
						ar.getErrors().add(diag_X);
						Diagnostic diag_Y = new Diagnostic(new Range(file.getPos(ir.getEndPos()-1), file.getPos(ir.getEndPos())), "LSP: Symbol index "+symIdx+" reported at different positions: " + ir.getEndPos()+ " and "+symEndPos);
						diag_Y.setSeverity(DiagnosticSeverity.Error);
						ar.getErrors().add(diag_Y);
					}
				}
				if (ar.getExportedSymbolRefs().containsKey(symIdx))
					throw new IllegalStateException("Duplicate symbol index "+symIdx);
				ar.getExportedSymbolRefs().put(symIdx, new IdentifierReference(symMod, symEndPos));
				ar.getModuleDeps().computeIfAbsent(symMod, x -> new EnumMap<>(ModuleDepType.class)).computeIfAbsent(ModuleDepType.DEFINITION, x -> new HashMap<>()).computeIfAbsent(symEndPos, x -> new ArrayList<>()).add(symIdx);
				break;
			case ANSWER_SyntaxElement:
				int start = readIntLE();
				int end = readIntLE();
				SyntaxElement synElem = SyntaxElement.read(dis);
				int defEnd = readIntLE();
				String defModName = defEnd == -1 ? null : readCStr();
				prevSymbolEnd = lastSymbolEnd;
				lastSymbolEnd = end;
				if (end > file.getContent().length()) end = file.getContent().length();
				String name = file.getContent().substring(start, end);
				IdentifierReference definition = defEnd == -1 ? null : new IdentifierReference(defModName, defEnd);
				boolean declaration = defEnd == end && (defModName.equals(ar.getModuleName()) || ar.getModuleName() == null);
				if (synElem == SyntaxElement.SynOverrideReference) {
					if (defEnd != -1) {
						OberonFile.Identifier id = new OberonFile.Identifier(start, end, SyntaxElement.SynProcedure.kind, definition);
						ar.getIdReferences().put(end, id);
						ar.getModuleDeps().computeIfAbsent(defModName, x -> new EnumMap<>(ModuleDepType.class)).computeIfAbsent(ModuleDepType.DEFINITION, x -> new HashMap<>()).computeIfAbsent(defEnd, x -> new ArrayList<>()).add(end);
						ar.getModuleDeps().computeIfAbsent(defModName, x -> new EnumMap<>(ModuleDepType.class)).computeIfAbsent(ModuleDepType.OVERRIDING, x -> new HashMap<>()).computeIfAbsent(defEnd, x -> new ArrayList<>()).add(end);
						Identifier overridingDefinition = ar.getIdDefinitions().get(end);
						if (overridingDefinition != null) {
							overridingDefinition.setUsed(true);
							overridingDefinition.setOverriding(true);
						} else {
							overridingPositions.add(end);
						}
					}
					break;
				}
				int[] semanticInfo = new int[] {
						end - start, synElem.tokenType,
						synElem.tokenModifiers | (declaration ? (1 << TOKEN_MODIFIERS.indexOf(SemanticTokenModifiers.Declaration)) : 0)
				};
				if (name.contains("\n")) {
					int currPos = name.indexOf('\n'), lastPos = currPos + 1;
					semanticInfo[0] = currPos;
					semanticTokenInformation.put(start + currPos, new int[] { currPos, semanticInfo[1], semanticInfo[2] });
					while ((currPos = name.indexOf('\n', lastPos)) != -1) {
						semanticTokenInformation.put(start + currPos, new int[] { currPos - lastPos, semanticInfo[1], semanticInfo[2] });
						lastPos = currPos + 1;
					}
					semanticInfo[0] = name.length() - lastPos;
				}
				semanticTokenInformation.put(end, semanticInfo);
				if (declaration && synElem.kind != null) {
					List<DocumentSymbol> outline = outlineStack.get(outlineStack.size() - 1);
					Range range = new Range(file.getPos(start), file.getPos(end));
					DocumentSymbol ds = new DocumentSymbol(name, synElem.kind, range, range);
					OberonFile.Identifier newId = new OberonFile.Identifier(start, end, synElem.kind, null);
					if (synElem == SyntaxElement.SynProcedure && overridingPositions.contains(end)) {
						newId.setUsed(true);
						newId.setOverriding(true);
					}
					if (synElem.kind == SymbolKind.Function && functionPending) {
						functionPending = false;
						ds.setRange(procRangeStack.get(procRangeStack.size() - 1));
						ds.setChildren(new ArrayList<>());
						ar.getFunctionDefinitions().put(end, newId);
						ar.getFunctionRanges().get(functionNamePosStack.get(functionNamePosStack.size() - 1))[0] = end;
						outlineStack.add(ds.getChildren());
					}
					outline.add(ds);
					if (inProcParam)
						newId.setProcedureParameter(true);
					ar.getIdDefinitions().put(end, newId);
					if (synElem == SyntaxElement.SynModule && name.equals(defModName) && ar.getModuleName() == null) {
						ar.getIdDefinitions().put(1, newId);
					}
				} else if (defEnd != -1 && synElem.kind != null) {
					OberonFile.Identifier id = new OberonFile.Identifier(start, end, synElem.kind, definition);
					ar.getIdReferences().put(end, id);
					ar.getModuleDeps().computeIfAbsent(defModName, x -> new EnumMap<>(ModuleDepType.class)).computeIfAbsent(ModuleDepType.DEFINITION, x -> new HashMap<>()).computeIfAbsent(defEnd, x -> new ArrayList<>()).add(end);
					if (defEnd == 1) {
						if (!defModName.equals(ar.getModuleName())) {
							// import statement
							if (importAliasPending) {
								importAliasPending = false;
							} else {
								ar.getIdDefinitions().put(end, id);
							}
						} else if (ar.getIdDefinitions().get(1) != null) {
							// Module END.
							ar.getModuleDeps().get(defModName).get(ModuleDepType.DEFINITION).computeIfAbsent(ar.getIdDefinitions().get(1).getEndPos(), x -> new ArrayList<>()).add(end);
						}
					}
				} else if (synElem == SyntaxElement.SynUndefined) {
					lastUndefinedSymbol = new OberonFile.Identifier(start, end, null, null);
				}
				break;
			case ANSWER_ProcedureStart:
				int procPos = lastSymbolEnd - "PROCEDURE".length();
				procRangeStack.add(new Range(file.getPos(procPos), file.getPos(lastSymbolEnd)));
				if (functionNamePosStack.size() == 0) {
					ar.getFunctionRanges().put(procPos, new int[] { -1, -1 });
				} else {
					ar.getFunctionRanges().put(procPos, new int[] { -1, -1,  functionNamePosStack.get(functionNamePosStack.size() - 1) });
				}
				functionNamePosStack.add(procPos);
				functionPending = true;
				break;
			case ANSWER_ProcedureEnd:
				int pos0 = adjustPos(readIntLE(), file.getContent().length());
				procRangeStack.remove(procRangeStack.size() - 1).setEnd(file.getPos(pos0));
				ar.getFunctionRanges().get(functionNamePosStack.remove(functionNamePosStack.size() - 1))[1] = lastSymbolEnd;
				if (!functionPending) {
					outlineStack.remove(outlineStack.size() - 1);
				}
				functionPending = false;
				break;
			case ANSWER_ImportAlias:
				// find last reference and remove it
				int lastPos = ar.getIdReferences().lastKey();
				Identifier id = ar.getIdReferences().remove(lastPos);
				ar.getModuleDeps().get(id.getDefinition().getModule()).get(ModuleDepType.DEFINITION).remove(id.getDefinition().getEndPos());
				if (id.getKind() != SymbolKind.Module) {
					throw new IllegalStateException("Last symbol to remove is not an imported module");
				}
				importAliasPending = true;
				break;
			case ANSWER_ProcParamStart:
				int pos1 = readIntLE();
				paramDepth++;
				ar.getParamTags().put(pos1, ParamTag.PROC_START);
				inProcParam = true;
				break;
			case ANSWER_CallParamStart:
				int pos2 = readIntLE();
				paramDepth++;
				ar.getParamTags().put(pos2, ParamTag.CALL_START);
				inProcParam = false;
				break;
			case ANSWER_ParamNext:
				int pos3 = readIntLE();
				ar.getParamTags().put(pos3, ParamTag.NEXT);
				break;
			case ANSWER_ParamEnd:
				int pos4 = readIntLE();
				paramDepth--;
				ar.getParamTags().put(pos4, paramDepth == 0 ? ParamTag.END_LAST : ParamTag.END);
				if (paramDepth == 0) inProcParam = false;
				break;
			case ANSWER_ForwardPointer:
				if (lastUndefinedSymbol != null && lastUndefinedSymbol.getEndPos() == lastSymbolEnd) {
					semanticTokenInformation.get(lastSymbolEnd)[1] = SyntaxElement.SynType.tokenType;
					ar.getIdReferences().put(lastSymbolEnd, new OberonFile.Identifier(lastUndefinedSymbol.getStartPos(),lastSymbolEnd, SyntaxElement.SynType.kind, new IdentifierReference(ar.getModuleName(), lastSymbolEnd)));
				}
				break;
			case ANSWER_ForwardPointerFixup:
				int pointerPos = readIntLE();
				int targetPos = readIntLE();
				OberonFile.Identifier pointerId = ar.getIdReferences().get(pointerPos);
				pointerId.setDefinition(new IdentifierReference(pointerId.getDefinition().getModule(), targetPos));
				ar.getModuleDeps().computeIfAbsent(pointerId.getDefinition().getModule(), x -> new EnumMap<>(ModuleDepType.class)).computeIfAbsent(ModuleDepType.DEFINITION, x -> new HashMap<>()).computeIfAbsent(targetPos, x -> new ArrayList<>()).add(pointerPos);
				break;
			case ANSWER_VarModified:
				Identifier varId = ar.getIdReferences().get(lastSymbolEnd);
				if (varId != null) {
					int tokenType = semanticTokenInformation.get(lastSymbolEnd)[1];
					if (tokenType == SyntaxElement.SynVariable.tokenType || tokenType == SyntaxElement.SynParameter.tokenType) {
						semanticTokenInformation.get(lastSymbolEnd)[2] |=1 << TOKEN_MODIFIERS.indexOf(SemanticTokenModifiers.Modification);
					}
					varId.setWrittenTo(true);
				}
				break;
			case ANSWER_VarProcParam:
				Identifier varId2 = ar.getIdDefinitions().get(lastSymbolEnd);
				if (varId2 != null) {
					varId2.setProcedureParameter(true);
				}
				break;
			case ANSWER_NameExported:
				Identifier expoId = ar.getIdDefinitions().get(prevSymbolEnd);
				if (expoId != null) {
					expoId.setExportedPos(lastSymbolEnd);
					lastExport = expoId;
				}
				break;
			case ANSWER_NameExportedAt:
				int exportedPos = readIntLE();
				Identifier expoId2 = ar.getIdDefinitions().get(lastSymbolEnd);
				if (expoId2 != null) {
					expoId2.setExportedPos(exportedPos);
					lastExport = expoId2;
				}
				break;
			case ANSWER_CommandExported:
				if (lastExport != null) {
					lastExport.setCommand(true);
				}
			case ANSWER_DefinitionRepeat:
				Identifier repeatId = ar.getIdReferences().get(lastSymbolEnd);
				if (repeatId != null) {
					repeatId.setDefinitionRepeat(true);
				}
				break;
			case ANSWER_DefinitionUsed:
				Identifier usedId = ar.getIdDefinitions().get(lastSymbolEnd);
				if (usedId != null) {
					usedId.setUsed(true);
				}
				break;
			case ANSWER_DeclarationBlockStart:
				if (declarationBlockStart != -1) throw new IllegalStateException("Nested declaration blocks");
				declarationBlockStart = readIntLE();
				break;
			case ANSWER_DeclarationBlockEnd:
				if (declarationBlockStart == -1) throw new IllegalStateException("No declaration block open");
				int declarationBlockEnd = readIntLE();
				ar.getDeclarationBlocks().put(declarationBlockStart, declarationBlockEnd);
				declarationBlockStart = -1;
				break;
			case ANSWER_DefinitionListStart:
				int[] definitionListS = new int[] {readIntLE(), -1, -1};
				definitionLists.add(definitionListS);
				ar.getDefinitionLists().put(definitionListS[0], definitionListS);
				break;
			case ANSWER_DefinitionListValue:
				if (definitionLists.isEmpty())
					throw new IllegalStateException("No definition list open");
				definitionLists.get(definitionLists.size()-1)[1] = readIntLE();
				break;
			case ANSWER_DefinitionListEnd:
				if (definitionLists.isEmpty())
					throw new IllegalStateException("No definition list open");
				int[] definitionListE = definitionLists.remove(definitionLists.size()-1);
				definitionListE[2] = readIntLE();
				if (definitionListE[1] == -1) definitionListE[1] = definitionListE[2];
				break;
			case ANSWER_RecordStart:
				List<DocumentSymbol> newOutline = new ArrayList<>(), oldOutline = outlineStack.get(outlineStack.size() - 1);
				if (!oldOutline.isEmpty()) {
					oldOutline.get(oldOutline.size() - 1).setChildren(newOutline);
				}
				outlineStack.add(newOutline);
				break;
			case ANSWER_RecordEnd:
				outlineStack.remove(outlineStack.size() - 1);
				break;
			case ANSWER_RecordEndAsProcedure:
				int procStart = readIntLE(), procEnd = readIntLE(), procIdentEnd = readIntLE();
				if (procIdentEnd == -1) {
					// A2: OBJECT
					procIdentEnd = ar.getIdReferences().get(procEnd).getDefinition().getEndPos();
					if (procStart >= procIdentEnd) procStart = procIdentEnd - 1;
				}
				Range procRange = new Range(file.getPos(procStart), file.getPos(procEnd));
				outlineStack.remove(outlineStack.size() - 1);
				List<DocumentSymbol> oldOutline2 = outlineStack.get(outlineStack.size() - 1);
				if (!oldOutline2.isEmpty()) {
					oldOutline2.get(oldOutline2.size() - 1).setRange(procRange);
				}
				ar.getFunctionDefinitions().put(procEnd, ar.getIdDefinitions().get(procEnd));
				List<Integer> funcRangeKeys = new ArrayList<>(ar.getFunctionRanges().subMap(procStart, procEnd).keySet());
				for(Integer funcRangeKey : funcRangeKeys) {
					int[] oldRange = ar.getFunctionRanges().get(funcRangeKey);
					if (oldRange.length == 2)
						ar.getFunctionRanges().put(funcRangeKey, new int[] { oldRange[0], oldRange[1], procStart});
				}
				ar.getFunctionRanges().put(procStart, new int[] { procIdentEnd, procEnd });
				break;
			default:
				throw new IOException("Invalid response: " + res);
			}
		}
		for(int[] pendingDef : definitionLists) {
			ar.getErrors().add(new Diagnostic(new Range(file.getPos(pendingDef[0]), file.getPos(pendingDef[0] + 1)), "LSP: Definition list not closed"));
			ar.getDefinitionLists().remove(pendingDef[0]);
		}
		for(Identifier id : ar.getIdDefinitions().values()) {
			if (id.isExported() || id.isUsed()) continue;
			if (id.getEndPos() == 1 || (ar.getIdDefinitions().get(1) != null && id.getEndPos() == ar.getIdDefinitions().get(1).getEndPos()))
				continue;
			Map<ModuleDepType, Map<Integer, List<Integer>>> deps0 = ar.getModuleDeps().get(ar.getModuleName());
			Map<Integer, List<Integer>> deps = deps0 == null ? null : deps0.get(ModuleDepType.DEFINITION);
			if (deps == null || deps.get(id.getEndPos()) == null || deps.get(id.getEndPos()).stream().filter(ep -> ep >= 0).map(ep -> ar.getIdReferences().get(ep)).allMatch(rid -> rid.isDefinitionRepeat())) {
				Diagnostic diag = new Diagnostic(new Range(file.getPos(id.getStartPos()), file.getPos(id.getEndPos())), id.isProcedureParameter() ? OberonFile.UNUSED_PARAM : OberonFile.UNUSED_DEFINITION);
				diag.setSeverity(DiagnosticSeverity.Hint);
				diag.setTags(Arrays.asList(DiagnosticTag.Unnecessary));
				ar.getErrors().add(diag);
			}
		}
		int[] semanticTokenBuffer = new int[5 * semanticTokenInformation.size()];
		int semanticTokenLength = 0, semanticTokenLine = 0, semanticTokenChar = 0;
		for(Map.Entry<Integer, int[]> entry : semanticTokenInformation.entrySet()) {
			int start = entry.getKey() - entry.getValue()[0];
			Position startPos = file.getPos(start);
			semanticTokenBuffer[semanticTokenLength++] = startPos.getLine() - semanticTokenLine;
			semanticTokenBuffer[semanticTokenLength++] = startPos.getCharacter() - (startPos.getLine() == semanticTokenLine ? semanticTokenChar : 0);
			semanticTokenBuffer[semanticTokenLength++] = entry.getValue()[0];
			semanticTokenBuffer[semanticTokenLength++] =entry.getValue()[1];
			semanticTokenBuffer[semanticTokenLength++] = entry.getValue()[2];
			semanticTokenLine = startPos.getLine();
			semanticTokenChar = startPos.getCharacter();
		}
		if (semanticTokenLength != semanticTokenBuffer.length)
			throw new IllegalStateException();
		ar.setSemanticTokens(semanticTokenBuffer);
		if (outlineStack.size() == 1) {
			for (DocumentSymbol ds : outlineStack.get(0)) {
				ar.getOutline().add(Either.forRight(ds));
			}
		}
		return result;
	}

	private int adjustPos(int pos, int len) {
		if (pos == len + 1)
			pos--;
		if (pos == len)
			pos--;
		return pos;
	}

	public synchronized List<CompletionItem> complete(String completionPrefix) throws IOException {
		ProtocolConstant.INST_AutoComplete.send(dos);
		writeIntLE(completionPrefix.length());
		dos.write(completionPrefix.getBytes(StandardCharsets.ISO_8859_1));
		dos.flush();
		List<CompletionItem> result = new ArrayList<>();
		loop: while (true) {
			ProtocolConstant res = ProtocolConstant.read(dis);
			switch (res) {
			case STATUS_OK:
				break loop;
			case STATUS_Invalid:
				throw new IOException("Invalid status received");
			case ANSWER_Completion:
				SyntaxElement synElem = SyntaxElement.read(dis);
				String name = readCStr();
				CompletionItem ci = new CompletionItem(name);
				ci.setKind(synElem.ckind);
				result.add(ci);
				break;
			default:
				throw new IOException("Invalid response: " + res);
			}
		}
		return result;
	}

	public synchronized List<FormatTokenInfo> format(String content) throws IOException {
		ProtocolConstant.INST_ReFormat.send(dos);
		writeIntLE(content.length());
		dos.write(content.getBytes(StandardCharsets.ISO_8859_1));
		dos.flush();
		List<FormatTokenInfo> result = new ArrayList<>();
		FormatTokenInfo lastToken = null, lastCommentToken = null;
		loop: while (true) {
			ProtocolConstant res = ProtocolConstant.read(dis);
			switch (res) {
			case STATUS_OK:
				break loop;
			case STATUS_Invalid:
				throw new IOException("Invalid status received");
			case ANSWER_FormatToken:
				int start = readIntLE();
				int end = readIntLE();
				int categories = dis.readByte() & 0xFF;
				if (lastCommentToken != null && lastCommentToken != lastToken)
					lastCommentToken = null;
				if (lastCommentToken != null && categories / 10 == 3) categories -= 10;
				lastToken = new FormatTokenInfo(start, end, categories);
				result.add(lastToken);
				break;
			case ANSWER_FormatTokenUpdate:
				int catUpdate = dis.readByte() & 0xFF;
				if (lastCommentToken != null && catUpdate / 10 == 3) catUpdate -= 10;
				lastToken.updateCategories(catUpdate);
				break;
			case ANSWER_IndentNextLine:
				lastToken.indentNextLine();
				break;
			case ANSWER_OutdentThisLine:
				lastToken.outdentThisLine();
				break;
			case ANSWER_OutdentThisLineAndComment:
				if (lastCommentToken != null) {
					lastCommentToken.outdentThisLine();
				} else {
					lastToken.outdentThisLine();
				}
				break;
			case ANSWER_TokenIsComment:
				lastCommentToken = lastToken;
				break;
			default:
				throw new IOException("Invalid response: " + res);
			}
		}
		return result;
	}

	public synchronized void switchEmbeddedMode() throws IOException {
		ProtocolConstant.INST_SwitchEmbeddedMode.send(dos);
		dos.flush();
	}

	public synchronized void shutdown() throws Exception {
		ProtocolConstant.INST_Exit.send(dos);
		dos.flush();
		if (shutdown != null) shutdown.call();
	}

	private void writeIntLE(int v) throws IOException {
		dos.write((v >>> 0) & 0xFF);
		dos.write((v >>> 8) & 0xFF);
		dos.write((v >>> 16) & 0xFF);
		dos.write((v >>> 24) & 0xFF);
	}

	private int readIntLE() throws IOException {
		int ch1 = dis.read();
		int ch2 = dis.read();
		int ch3 = dis.read();
		int ch4 = dis.read();
		if ((ch1 | ch2 | ch3 | ch4) < 0)
			throw new EOFException();
		return ((ch4 << 24) + (ch3 << 16) + (ch2 << 8) + (ch1 << 0));
	}

	private String readCStr() throws IOException {
		byte b;
		StringBuilder sb = new StringBuilder();
		while ((b = dis.readByte()) != 0) {
			sb.append((char) b);
		}
		return sb.toString();
	}

	private static enum ProtocolConstant {

		/* instruction codes */
		INST_GetModuleInfo(100), INST_AutoComplete(101), INST_ReFormat(102), INST_SwitchEmbeddedMode(103), INST_Exit(104),

		/* answer packets */
		ANSWER_ModuleName(10), ANSWER_ModuleImport(11), ANSWER_Error(12), ANSWER_SymbolFileChanged(13),
		ANSWER_SyntaxElement(14), ANSWER_ProcedureStart(15), ANSWER_ProcedureEnd(16), ANSWER_VarModified(17),
		ANSWER_RecordStart(18), ANSWER_RecordEnd(19), ANSWER_NameExported(20), ANSWER_ImportAlias(21),
		ANSWER_ProcParamStart(22), ANSWER_CallParamStart(23), ANSWER_ParamNext(24), ANSWER_ParamEnd(25),
		ANSWER_ForwardPointer(26), ANSWER_ForwardPointerFixup(27), ANSWER_DefinitionRepeat(28),
		ANSWER_DefinitionUsed(29), ANSWER_DeclarationBlockStart(30), ANSWER_DeclarationBlockEnd(31),
		ANSWER_DefinitionListStart(32), ANSWER_DefinitionListValue(33), ANSWER_DefinitionListEnd(34),
		ANSWER_SymbolFileIndex(35), ANSWER_CommandExported(36),

		/* answer packets / A2 */
		ANSWER_Warning(60), ANSWER_Information(61), ANSWER_NameExportedAt(62), ANSWER_VarProcParam(63),
		ANSWER_RecordEndAsProcedure(64),

		/* autocomplete answer packets */
		ANSWER_Completion(80),

		/* reformat answer packets */
		ANSWER_FormatToken(90), ANSWER_FormatTokenUpdate(91), ANSWER_IndentNextLine(92), ANSWER_OutdentThisLine(93),
		ANSWER_OutdentThisLineAndComment(94), ANSWER_TokenIsComment(95),

		/* status codes, where needed */
		STATUS_OK(0), STATUS_Invalid(1);

		private byte value;

		private ProtocolConstant(int value) {
			this.value = (byte) value;
		}

		private void send(DataOutputStream dos) throws IOException {
			dos.writeByte(value);
		}

		private static ProtocolConstant read(DataInputStream dis) throws IOException {
			byte b = dis.readByte();
			for (ProtocolConstant v : values())
				if (v.value == b)
					return v;
			throw new IOException("Invalid byte received: " + (b & 0xff));
		}
	}

	private static enum SyntaxElement {

		SynOperator(1, null, null, SemanticTokenTypes.Operator),
		SynType(2, SymbolKind.Class, CompletionItemKind.Class, SemanticTokenTypes.Type),
		SynKeyword(3, null, CompletionItemKind.Keyword, SemanticTokenTypes.Keyword),
		SynString(4, null, null, SemanticTokenTypes.String),
		SynComment(5, null, null, SemanticTokenTypes.Comment),
		SynConstant(6, SymbolKind.Constant, CompletionItemKind.Constant, SemanticTokenTypes.Variable, SemanticTokenModifiers.Readonly),
		SynUndefined(7, null, null, null),
		SynOverrideReference(13, null, null, null),
		SynModule(8, SymbolKind.Module, CompletionItemKind.Module, SemanticTokenTypes.Namespace),
		SynVariable(9, SymbolKind.Variable, CompletionItemKind.Variable, SemanticTokenTypes.Variable),
		SynParameter(10, SymbolKind.TypeParameter, CompletionItemKind.Variable, SemanticTokenTypes.Parameter),
		SynRecordField(11, SymbolKind.Field, CompletionItemKind.Field, SemanticTokenTypes.Property),
		SynProcedure(12, SymbolKind.Function, CompletionItemKind.Function, SemanticTokenTypes.Function);

		private final byte value;
		private final SymbolKind kind;
		private final CompletionItemKind ckind;
		private final int tokenType;
		private final int tokenModifiers;

		private SyntaxElement(int value, SymbolKind kind, CompletionItemKind ckind, String tokenType, String... tokenModifiers) {
			this.value = (byte) value;
			this.kind = kind;
			this.ckind = ckind;
			int tokenTypeValue = -1, tokenModifiersValue = 0;
			if (tokenType != null) {
				tokenTypeValue = TOKEN_TYPES.indexOf(tokenType);
				if (tokenTypeValue == -1)
					throw new IllegalStateException("Invalid token type "+ tokenType+" for "+value);
			}
			if (tokenModifiers != null && tokenModifiers.length > 0) {
				for (String mod : tokenModifiers) {
					int modVal = TOKEN_MODIFIERS.indexOf(mod);
					if (modVal == -1)
						throw new IllegalStateException();
					tokenModifiersValue |= (1 << modVal);
				}
			}
			this.tokenType = tokenTypeValue;
			this.tokenModifiers = tokenModifiersValue;
		}

		private static SyntaxElement read(DataInputStream dis) throws IOException {
			byte b = dis.readByte();
			for (SyntaxElement v : values())
				if (v.value == b)
					return v;
			throw new IOException("Invalid byte received: " + (b & 0xff));
		}
	}
}
