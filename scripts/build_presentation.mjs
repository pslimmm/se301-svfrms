// Authoring only: CODEX_NODE_MODULES, CODEX_PRESENTATIONS_SKILL and CODEX_PYTHON locate bundled tools.
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const modules=process.env.CODEX_NODE_MODULES;
const skill=process.env.CODEX_PRESENTATIONS_SKILL;
const python=process.env.CODEX_PYTHON;
if(!modules || !skill || !python)throw new Error('Resolve authoring runtime paths first');
process.env.RUNTIME_NODE_MODULES=modules;
const {Presentation,PresentationFile}=await import(pathToFileURL(path.join(modules,'@oai/artifact-tool/dist/artifact_tool.mjs')).href);
const {finalizePresentation}=await import(pathToFileURL(path.join(skill,'container_tools/artifact_tool_utils.mjs')).href);
const p=Presentation.create({slideSize:{width:1280,height:720}});
const tmp=path.join(root,'.superpowers/sdd/2026-10-31-svfrms-finalized/presentation-build');
const finalDir=path.join(root,'.superpowers/sdd/2026-10-31-svfrms-finalized/presentation-output');
await fs.mkdir(tmp,{recursive:true});await fs.mkdir(finalDir,{recursive:true});
const font='Calibri';
const text=(slide,value,x,y,w,h,size=30,bold=false,color='#172B3A')=>{
 const s=slide.shapes.add({geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0}});
 s.text=value;s.text.style={typeface:font,fontSize:size,bold,color,wrap:'square',autoFit:'none',insets:0};return s;
};
function slide(title,notes){const s=p.slides.add();s.background.fill='#FFFFFF';text(s,title,72,55,1136,100,p.slides.items.length===1?56:46,true);s.speakerNotes.text=notes;return s;}
function rows(s,entries){entries.forEach(([heading,body],i)=>{text(s,heading,76,190+i*145,1080,46,31,true);text(s,body,76,238+i*145,1080,84,28);});}
let s=slide('Preserving SVFRMS behavior',
 '30 seconds. Group3. We continued the existing GPT refactor, retained the original behavior oracle and repaired concrete reporting risks. Explain five sensors/four original chambers, then introduce the two-minute crop/concurrency demo after these six slides. Source: docs/technical-report.md; original a2ed2ed3266e1f676c0528515b52c1a33f2fabc8.');
text(s,'Group 3',76,225,1080,60,40,true);
text(s,'Java 25  |  Maven  |  One authoritative service graph',76,320,1080,90,32);
text(s,'Legacy behavior, dynamic crops and verifiable delivery',76,435,1080,100,34);
s=slide('The original risks were shared state and ownership',
 '45 seconds. Show concrete legacy examples: integer += and ArrayList mutation from multiple chamber threads; HashMap check-then-put; device/sensor read-modify-write; swallowed interruption. Both report methods lock alertLock then reportLock: do not claim an observed AB-BA deadlock. The consumer-failure/full-queue wait is the real liveness hazard. Source: original SVFRMS lines48-60,73-86,131-139,145-203,207-240.');
rows(s,[['Lost updates and competing state','Shared counters, lists and chamber fields had no consistent owner.'],['Queue liveness','A failed consumer can strand blocked producers; ignored interrupts prevent cancellation.'],['Preservation matters','Keep asymmetric rules, charges, controls, logs and report formats.']]);
s=slide('One reading follows focused components',
 '60 seconds. Describe Observer for logging/evaluation, Strategy/Registry for crop policy and Command for queued controls. One captured profile per reading. CAS updates are pure and replace complete immutable values. A short alert monitor protects composite alert state. Report/file storage are separate from state locks. Source: docs/diagrams/component.svg and current source packages.');
const labels=['Sensors','Telemetry','Evaluation','Control queue'];
labels.forEach((label,i)=>{const x=76+i*290;const box=s.shapes.add({geometry:'rect',position:{left:x,top:220,width:245,height:105},fill:'#EDF4F0',line:{fill:'#496258',width:2}});box.text=label;box.text.style={typeface:font,fontSize:30,bold:true,color:'#172B3A',alignment:'center',verticalAlignment:'middle'};if(i<3)text(s,'â†’',x+250,244,40,50,36,true);});
text(s,'Registry supplies policy; observers retain logs and account for alerts.',76,395,1120,80,30);
text(s,'Immutable chamber snapshots, scoped locks and post-join reports.',76,500,1120,80,30);
s=slide('Accepted work must finish or fail visibly',
 '75 seconds. Admission closes before stop; track admitted/completed work through output; preserve first failure; cancel sibling producers; test the real production JVM hook. Explain two final fixes: publish a closed sibling file to avoid hybrid reports; group report/archive presentation with a separate lock. Do not hold state locks during callbacks, file I/O or joins. Sources: ActuatorExecutionService, SVFRMS hook, FileReportStorageAdapter, DailyReportService and their regression tests.');
rows(s,[['Explicit lifecycle','Close admission, drain accepted commands and observe producer or consumer failure.'],['Complete report publication','Write a unique sibling file and replace; failed preparation preserves the old report.'],['Report operation boundaries','Archive cannot split the daily block; state updates and file saves remain independent.']]);
s=slide('Crop changes use the existing registry and engine',
 '45 seconds. Live demo registers Kale with illustrative Tomato-equivalent values, processes CH5, drains controls and removes Kale. A later temperature reading has no crop alert, while global light/nutrient fallback remains. Final result3 alerts/3 actions/10energy. No default profile changes or evaluator edits. Four additional names and in-flight replacement/removal are tested. Source: CropExtensionDemo, FeatureExtensionCropTest.');
rows(s,[['Register and process Kale','One immutable profile; CH5 receives an alert, charge and mitigation.'],['Remove and process again','Crop-specific checks stop on later readings; global light/nutrient rules remain.'],['Keep the default behavior','Six original profiles and four standard chambers are unchanged.']]);
const data=JSON.parse(await fs.readFile(path.join(root,'docs/finalization/verification-data.json'),'utf8'));
s=slide('Verification is evidence with stated limits',
 '45 seconds. State measured test/coverage results, including20 repetitions of critical schedules and frozen-original comparison. Four donor baselines were reverified; Astra missing JAR naming was recorded as a failure. All inherited branches remain in the denominator;100% applies to changed decisions, not every possible thread schedule. Explain user-directed DOCX format and preserved-scope mismatches; names and human rehearsal are deferred. Then switch to the two-minute demo. Sources: docs/finalization/verification-data.json, coverage-summary.json, branch-baseline-results.json and constraint-matrix.md.');
rows(s,[[`${data.tests.tests} tests pass; original-system oracle retained`,'Critical close, subscription and report-order schedules repeat 20 times.'],[`${(data.coverage.line.ratio*100).toFixed(1)}% lines; ${(data.coverage.branch.ratio*100).toFixed(1)}% branches`,'Every new or modified branch decision is covered; residual inherited paths are listed.'],['Verify the exact extracted submission','Rebuild source, execute its JAR and crop demo, inspect DOCX aliases and checksums.']]);
for(const [i,sl] of p.slides.items.entries()){
 const png=await p.export({slide:sl,format:'png',scale:1});
 await fs.writeFile(path.join(tmp,`slide-${i+1}.png`),new Uint8Array(await png.arrayBuffer()));
}
const candidate=path.join(tmp,'candidate.pptx');await(await PresentationFile.exportPptx(p)).save(candidate);
const finalPath=path.join(finalDir,`SVFRMS-FINALIZED-${Date.now()}.pptx`);
await finalizePresentation({workspaceDir:root,candidatePath:candidate,finalPath,pythonExecutable:python,
 integrityValidatorPath:path.join(skill,'container_tools/inspect_presentation_package_integrity.py'),
 layoutValidatorPath:path.join(skill,'container_tools/inspect_presentation_layout_geometry.py'),
 layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-bullet-geometry','--validate-heading-fit'],
 fontPolicy:{basis:'design',families:[font]},explicitTotalSlideCount:6,
 requiredNativeTableOwnerSlides:[],requiredNativeChartOwnerSlides:[],verifyArtifactToolImport:true,
 receiptPath:path.join(tmp,path.basename(finalPath)+'.validation.json')});
const destination=path.join(root,'docs/presentation/SVFRMS-FINALIZED.pptx');
await fs.copyFile(finalPath,destination);
console.log(destination);
