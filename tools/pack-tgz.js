// 确定性打包：tar 条目的 mtime 固定为 0（不用墙钟），条目按相对路径排序，gzip level 固定。
// 因此「同样输入 → 同样 .tgz 字节」，便于比对与复现（Node 的 gzip 头默认不写 MTIME）。
// 注：本文件与 SQL 生成链无关；改这里只为消除同类墙钟量。
const fs=require("fs");const path=require("path");const zlib=require("zlib");
function walk(dir,base,out){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const p=path.join(dir,e.name);const rel=base?base+"/"+e.name:e.name;if(e.isDirectory())walk(p,rel,out);else out.push({rel,p});}return out;}
function tarHeader(name,size,mode){const b=Buffer.alloc(512);b.write(name,0,100,"utf8");b.write("0000644\0",100);b.write("0000000\0",108);b.write("0000000\0",116);b.write(size.toString(8).padStart(11,"0")+"\0",124);b.write("00000000000\0",136);b.write("        ",148);b.write("0",156);b.write("ustar\0",257);b.write("00",263);let s=0;for(const x of b)s+=x;b.write(s.toString(8).padStart(6,"0")+"\0",148);return b;}
function pack(srcDir,prefix,outFile){const files=walk(srcDir,"",[]);const parts=[];const manifest=[];
 for(const f of files.sort((a,b)=>a.rel.localeCompare(b.rel))){const data=fs.readFileSync(f.p);const name=prefix+"/"+f.rel;parts.push(tarHeader(name,data.length),data);const pad=data.length%512?(512-(data.length%512)):0;if(pad)parts.push(Buffer.alloc(pad));manifest.push(f.rel);}
 parts.push(Buffer.alloc(1024));const tar=Buffer.concat(parts);fs.writeFileSync(outFile,zlib.gzipSync(tar,{level:9}));return manifest;}
const src=process.argv[2],out=process.argv[3],prefix=process.argv[4]||"package";
const m=pack(src,prefix,out);
console.log("packed",m.length,"files ->",out,fs.statSync(out).size,"bytes gz");
