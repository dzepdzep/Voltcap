// Minimal glTF (GLB) renderer for Voltcap's pre-rendered 3D media. WebGL2, no libraries.
'use strict';
const canvas = document.getElementById('c');
const gl = canvas.getContext('webgl2', { antialias: false, alpha: true, premultipliedAlpha: true, preserveDrawingBuffer: true });

// ---------- maths (column-major 4x4) ----------
const M4 = {
  ident() { const m = new Float32Array(16); m[0] = m[5] = m[10] = m[15] = 1; return m; },
  mul(a, b) { const o = new Float32Array(16); for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) { let s = 0; for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k]; o[c * 4 + r] = s; } return o; },
  trans(x, y, z) { const m = M4.ident(); m[12] = x; m[13] = y; m[14] = z; return m; },
  scale(x, y, z) { const m = M4.ident(); m[0] = x; m[5] = y; m[10] = z; return m; },
  rotY(d) { const t = d * Math.PI / 180, c = Math.cos(t), s = Math.sin(t), m = M4.ident(); m[0] = c; m[2] = -s; m[8] = s; m[10] = c; return m; },
  rotX(d) { const t = d * Math.PI / 180, c = Math.cos(t), s = Math.sin(t), m = M4.ident(); m[5] = c; m[6] = s; m[9] = -s; m[10] = c; return m; },
  quat(q) { const [x, y, z, w] = q; const m = M4.ident();
    m[0] = 1 - 2 * (y * y + z * z); m[1] = 2 * (x * y + z * w); m[2] = 2 * (x * z - y * w);
    m[4] = 2 * (x * y - z * w); m[5] = 1 - 2 * (x * x + z * z); m[6] = 2 * (y * z + x * w);
    m[8] = 2 * (x * z + y * w); m[9] = 2 * (y * z - x * w); m[10] = 1 - 2 * (x * x + y * y); return m; },
  persp(fovDeg, aspect, near, far) { const f = 1 / Math.tan(fovDeg * Math.PI / 360), m = new Float32Array(16);
    m[0] = f / aspect; m[5] = f; m[10] = (far + near) / (near - far); m[11] = -1; m[14] = 2 * far * near / (near - far); return m; },
  apply(m, p) { const x = p[0], y = p[1], z = p[2];
    const w = m[3] * x + m[7] * y + m[11] * z + m[15];
    return [(m[0] * x + m[4] * y + m[8] * z + m[12]) / w, (m[1] * x + m[5] * y + m[9] * z + m[13]) / w, (m[2] * x + m[6] * y + m[10] * z + m[14]) / w]; },
  normal3(m) { // inverse transpose of the upper 3x3
    const a = m[0], b = m[4], c = m[8], d = m[1], e = m[5], f = m[9], g = m[2], h = m[6], i = m[10];
    const A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g, D = -(b * i - c * h), E = a * i - c * g, F = -(a * h - b * g), G = b * f - c * e, H = -(a * f - c * d), I = a * e - b * d;
    const det = a * A + b * B + c * C; const k = 1 / det;
    return new Float32Array([A * k, B * k, C * k, D * k, E * k, F * k, G * k, H * k, I * k]); }
};

// ---------- GLB ----------
function parseGLB(buf) {
  const dv = new DataView(buf); let off = 12; let json = null, bin = null;
  while (off < dv.byteLength) {
    const len = dv.getUint32(off, true), type = dv.getUint32(off + 4, true);
    const chunk = buf.slice(off + 8, off + 8 + len);
    if (type === 0x4E4F534A) json = JSON.parse(new TextDecoder().decode(chunk)); else bin = chunk;
    off += 8 + len;
  }
  return { json, bin };
}
const COMP = { 5120: Int8Array, 5121: Uint8Array, 5122: Int16Array, 5123: Uint16Array, 5125: Uint32Array, 5126: Float32Array };
const NCOMP = { SCALAR: 1, VEC2: 2, VEC3: 3, VEC4: 4, MAT4: 16 };
function readAccessor(j, bin, idx) {
  const a = j.accessors[idx], bv = j.bufferViews[a.bufferView];
  const T = COMP[a.componentType], n = NCOMP[a.type], es = T.BYTES_PER_ELEMENT;
  const stride = bv.byteStride || es * n, base = (bv.byteOffset || 0) + (a.byteOffset || 0);
  const out = new (a.componentType === 5126 || a.normalized ? Float32Array : (a.componentType === 5125 ? Uint32Array : Uint32Array))(a.count * n);
  const dv = new DataView(bin);
  const get = { 5120: (o) => dv.getInt8(o), 5121: (o) => dv.getUint8(o), 5122: (o) => dv.getInt16(o, true), 5123: (o) => dv.getUint16(o, true), 5125: (o) => dv.getUint32(o, true), 5126: (o) => dv.getFloat32(o, true) }[a.componentType];
  const norm = a.normalized ? ({ 5120: 127, 5121: 255, 5122: 32767, 5123: 65535 }[a.componentType]) : 1;
  for (let i = 0; i < a.count; i++) for (let c = 0; c < n; c++) out[i * n + c] = get(base + i * stride + c * es) / norm;
  return out;
}

// ---------- shaders ----------
const VS = `#version 300 es
in vec3 aPos; in vec3 aNor; in vec2 aUv;
uniform mat4 uMV, uP; uniform mat3 uN;
out vec3 vPos; out vec3 vNor; out vec2 vUv;
void main(){ vec4 p = uMV*vec4(aPos,1.0); vPos=p.xyz; vNor=uN*aNor; vUv=aUv; gl_Position=uP*p; }`;
const FS = `#version 300 es
precision highp float;
in vec3 vPos; in vec3 vNor; in vec2 vUv;
uniform vec4 uBase; uniform int uHasTex; uniform sampler2D uTex;
uniform float uMetal, uRough, uCut, uArt, uExpo, uTrans; uniform int uMode, uPass;
out vec4 o;
const float PI=3.14159265;
vec3 aces(vec3 x){ return clamp(x*(2.51*x+0.03)/(x*(2.43*x+0.59)+0.14),0.0,1.0); }
vec3 toSrgb(vec3 c){ return mix(c*12.92, 1.055*pow(c,vec3(1.0/2.4))-0.055, step(0.0031308,c)); }
float D(float nh,float a){ float a2=a*a; float d=nh*nh*(a2-1.0)+1.0; return a2/(PI*d*d); }
float G1(float x,float k){ return x/(x*(1.0-k)+k); }
vec3 light(vec3 N, vec3 V, vec3 L, vec3 col, vec3 alb, float metal, float rough, vec3 F0){
  float nl=max(dot(N,L),0.0); if(nl<=0.0) return vec3(0.0);
  vec3 H=normalize(L+V); float nh=max(dot(N,H),0.0), nv=max(dot(N,V),1e-3), vh=max(dot(V,H),0.0);
  float a=max(rough*rough,0.002); float k=(rough+1.0)*(rough+1.0)/8.0;
  vec3 F=F0+(1.0-F0)*pow(1.0-vh,5.0);
  vec3 spec=D(nh,a)*G1(nl,k)*G1(nv,k)*F/(4.0*nl*nv);
  vec3 kd=(1.0-F)*(1.0-metal);
  return (kd*alb/PI + spec)*col*nl*PI;
}
void main(){
  vec4 base=uBase; if(uHasTex==1) base*=texture(uTex,vUv);
  if(uArt>=0.0){ base=vec4(vec3(uArt),1.0); }
  if(uMode==1 && base.a<uCut) discard;
  if(uMode!=2) base.a=1.0;
  vec3 N=normalize(vNor); if(!gl_FrontFacing) N=-N;
  vec3 V=normalize(-vPos);
  float metal=uMetal, rough=clamp(uRough,0.04,1.0);
  if(uArt>=0.0){ metal=0.0; rough=0.6; }
  vec3 alb=base.rgb; vec3 F0=mix(vec3(0.04),alb,metal);
  vec3 cd=vec3(0.0), cs=vec3(0.0);
  vec3 Ls[3]; vec3 Cs[3];
  Ls[0]=normalize(vec3(-0.45,0.65,0.75)); Cs[0]=vec3(1.75);
  Ls[1]=normalize(vec3(0.75,0.15,0.55));  Cs[1]=vec3(0.6);
  Ls[2]=normalize(vec3(0.15,0.8,-0.6));   Cs[2]=vec3(0.9);
  float nv=max(dot(N,V),1e-3);
  float a=max(rough*rough,0.002); float k=(rough+1.0)*(rough+1.0)/8.0;
  for(int i=0;i<3;i++){
    vec3 L=Ls[i]; float nl=max(dot(N,L),0.0); if(nl<=0.0) continue;
    vec3 H=normalize(L+V); float nh=max(dot(N,H),0.0), vh=max(dot(V,H),0.0);
    vec3 F=F0+(1.0-F0)*pow(1.0-vh,5.0);
    cs+=D(nh,a)*G1(nl,k)*G1(nv,k)*F/(4.0*nl*nv)*Cs[i]*nl*PI;
    cd+=(1.0-F)*(1.0-metal)*alb*Cs[i]*nl;
  }
  vec3 hemi=mix(vec3(0.07,0.07,0.08),vec3(0.30,0.31,0.34),N.y*0.5+0.5);
  cd+=alb*(1.0-metal)*hemi;
  vec3 R=reflect(-V,N);
  vec3 Fr=F0+(max(vec3(1.0-rough),F0)-F0)*pow(1.0-nv,5.0);
  float box=pow(max(dot(R,normalize(vec3(-0.35,0.7,0.6))),0.0), mix(6.0,180.0,(1.0-rough)*(1.0-rough)));
  vec3 env=mix(vec3(0.04),vec3(0.45),smoothstep(-0.3,0.7,R.y)) + vec3(1.8)*box*(1.0-rough*0.6);
  vec3 se=env*Fr*(1.0-rough*0.55);
  if(metal>0.0) se*=mix(vec3(1.0),alb*1.6,metal);
  cs+=se;
  if(uMode==3){
    // see-through tinted plastic: the light behind it passes, tinted ([uPass] 0 = multiply what's behind, 1 = add its own)
    vec3 T=clamp(uTrans*alb,0.0,1.0); float tA=dot(T,vec3(0.3333));
    if(uPass==0){ o=vec4(toSrgb(T),tA); return; }
    vec3 s=toSrgb(aces((cd*(1.0-uTrans)+cs)*uExpo));
    o=vec4(s*(1.0-tA),1.0-tA); return;
  }
  vec3 s=toSrgb(aces((cd+cs)*uExpo));
  float al=base.a;
  if(uMode==2){ float sp=dot(se,vec3(0.3333)); al=clamp(al+sp*0.5,0.0,1.0); }
  o=vec4(s*al,al);
}`;
function compile(type, src) { const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s)); return s; }
const prog = gl.createProgram();
gl.attachShader(prog, compile(gl.VERTEX_SHADER, VS)); gl.attachShader(prog, compile(gl.FRAGMENT_SHADER, FS));
gl.bindAttribLocation(prog, 0, 'aPos'); gl.bindAttribLocation(prog, 1, 'aNor'); gl.bindAttribLocation(prog, 2, 'aUv');
gl.linkProgram(prog); if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(prog));
const U = {}; for (const n of ['uMV', 'uP', 'uN', 'uBase', 'uHasTex', 'uTex', 'uMetal', 'uRough', 'uCut', 'uArt', 'uMode', 'uExpo', 'uTrans', 'uPass']) U[n] = gl.getUniformLocation(prog, n);

// ---------- scene ----------
async function loadScene(url) {
  const buf = await (await fetch(url)).arrayBuffer();
  const { json: j, bin } = parseGLB(buf);
  const textures = [];
  for (const t of (j.textures || [])) {
    const img = j.images[t.source]; const bv = j.bufferViews[img.bufferView];
    const blob = new Blob([new Uint8Array(bin, bv.byteOffset || 0, bv.byteLength)], { type: img.mimeType });
    const bmp = await createImageBitmap(blob, { colorSpaceConversion: 'none', premultiplyAlpha: 'none' });
    const tex = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, tex);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false); gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.SRGB8_ALPHA8, gl.RGBA, gl.UNSIGNED_BYTE, bmp);
    gl.generateMipmap(gl.TEXTURE_2D);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    textures.push(tex);
  }
  const prims = []; const lo = [1e9, 1e9, 1e9], hi = [-1e9, -1e9, -1e9];
  function visit(ni, parent) {
    const n = j.nodes[ni]; let m;
    if (n.matrix) m = new Float32Array(n.matrix);
    else { m = M4.ident(); if (n.translation) m = M4.mul(m, M4.trans(...n.translation)); if (n.rotation) m = M4.mul(m, M4.quat(n.rotation)); if (n.scale) m = M4.mul(m, M4.scale(...n.scale)); }
    const world = M4.mul(parent, m);
    if (n.mesh !== undefined) {
      const mesh = j.meshes[n.mesh];
      for (const p of mesh.primitives) {
        if (p.mode !== undefined && p.mode !== 4) continue;
        const pos = readAccessor(j, bin, p.attributes.POSITION);
        const nor = p.attributes.NORMAL !== undefined ? readAccessor(j, bin, p.attributes.NORMAL) : null;
        const uv = p.attributes.TEXCOORD_0 !== undefined ? readAccessor(j, bin, p.attributes.TEXCOORD_0) : null;
        const idx = p.indices !== undefined ? readAccessor(j, bin, p.indices) : null;
        let cx = 0, cy = 0, cz = 0; const cnt = pos.length / 3;
        for (let i = 0; i < cnt; i++) {
          const w = M4.apply(world, [pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]]);
          for (let k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], w[k]); hi[k] = Math.max(hi[k], w[k]); }
          cx += w[0]; cy += w[1]; cz += w[2];
        }
        const vao = gl.createVertexArray(); gl.bindVertexArray(vao);
        const vb = (data, loc, n2) => { const b = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, b); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(data), gl.STATIC_DRAW); gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, n2, gl.FLOAT, false, 0, 0); };
        vb(pos, 0, 3);
        if (nor) vb(nor, 1, 3); else { gl.disableVertexAttribArray(1); gl.vertexAttrib3f(1, 0, 0, 1); }
        if (uv) vb(uv, 2, 2); else { gl.disableVertexAttribArray(2); gl.vertexAttrib2f(2, 0, 0); }
        let count = cnt, type = null;
        if (idx) { const ib = gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ib); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint32Array(idx), gl.STATIC_DRAW); count = idx.length; type = gl.UNSIGNED_INT; }
        gl.bindVertexArray(null);
        const mat = p.material !== undefined ? j.materials[p.material] : {};
        const pbr = mat.pbrMetallicRoughness || {};
        const trans = ((mat.extensions || {}).KHR_materials_transmission || {}).transmissionFactor || 0;
        const mode = trans > 0 ? 3 : (mat.alphaMode === 'BLEND' ? 2 : (mat.alphaMode === 'MASK' ? 1 : 0));
        prims.push({ vao, count, type, world, name: mesh.name || n.name || '', nodeName: n.name || '', mat: mat.name || '', mode,
          base: pbr.baseColorFactor || [1, 1, 1, 1], tex: pbr.baseColorTexture ? textures[pbr.baseColorTexture.index] : null,
          metal: pbr.metallicFactor === undefined ? 1 : pbr.metallicFactor, rough: pbr.roughnessFactor === undefined ? 1 : pbr.roughnessFactor,
          cut: mat.alphaCutoff === undefined ? 0.5 : mat.alphaCutoff, trans, centroid: [cx / cnt, cy / cnt, cz / cnt] });
      }
    }
    for (const c of (n.children || [])) visit(c, world);
  }
  const scene = j.scenes[j.scene || 0];
  for (const ni of scene.nodes) visit(ni, M4.ident());
  return { prims, lo, hi };
}

// ---------- render targets ----------
let fbMS = null, fbRes = null, curSize = 0;
function targets(size) {
  if (size === curSize) return; curSize = size;
  canvas.width = size; canvas.height = size;
  fbMS = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, fbMS);
  const rbC = gl.createRenderbuffer(); gl.bindRenderbuffer(gl.RENDERBUFFER, rbC); gl.renderbufferStorageMultisample(gl.RENDERBUFFER, 4, gl.RGBA8, size, size);
  gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.RENDERBUFFER, rbC);
  const rbD = gl.createRenderbuffer(); gl.bindRenderbuffer(gl.RENDERBUFFER, rbD); gl.renderbufferStorageMultisample(gl.RENDERBUFFER, 4, gl.DEPTH_COMPONENT24, size, size);
  gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, rbD);
  fbRes = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, fbRes);
  const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t); gl.texStorage2D(gl.TEXTURE_2D, 1, gl.RGBA8, size, size);
  gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, t, 0);
}

function draw(sc, mv0, P, art, artPrefix, expo, size, ov) {
  ov = ov || {};
  gl.bindFramebuffer(gl.FRAMEBUFFER, fbMS); gl.viewport(0, 0, size, size);
  gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
  gl.useProgram(prog); gl.enable(gl.DEPTH_TEST); gl.disable(gl.CULL_FACE);
  gl.uniformMatrix4fv(U.uP, false, P); gl.uniform1f(U.uExpo, expo); gl.uniform1i(U.uTex, 0);
  const isArt = (p) => artPrefix.some(a => p.name.startsWith(a) || p.nodeName.startsWith(a));
  const hide = (p) => (ov.hide || []).includes(p.nodeName);
  const baseOf = (p) => {
    if (ov.tint !== undefined && ov.tint !== null) {
      const c = Array.isArray(ov.tint) ? ov.tint : [ov.tint, ov.tint, ov.tint];
      if (p.mat === 'molded_polymer' || p.mat === 'translucent_tinted_polycarbonate') return [c[0], c[1], c[2], p.base[3]];
      if (p.mat === 'recess_polymer') return [0.48 * c[0], 0.48 * c[1], 0.48 * c[2], p.base[3]];
    }
    if (ov.badge !== undefined && ov.badge !== null && p.mat === 'console_badge_polymer') { const c = Array.isArray(ov.badge) ? ov.badge : [ov.badge, ov.badge, ov.badge]; return [c[0], c[1], c[2], 1]; }
    return p.base;
  };
  const one = (p) => {
    if (hide(p)) return;
    const mv = M4.mul(mv0, p.world);
    gl.uniformMatrix4fv(U.uMV, false, mv); gl.uniformMatrix3fv(U.uN, false, M4.normal3(mv));
    gl.uniform4fv(U.uBase, baseOf(p)); gl.uniform1f(U.uMetal, p.metal); gl.uniform1f(U.uRough, p.rough); gl.uniform1f(U.uCut, p.cut);
    gl.uniform1i(U.uMode, p.mode); gl.uniform1f(U.uArt, isArt(p) ? art : (ov.logoWhite && p.nodeName === 'console_logo' ? 1.0 : -1)); gl.uniform1f(U.uTrans, p.trans || 0);
    if (p.tex) { gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, p.tex); gl.uniform1i(U.uHasTex, 1); } else gl.uniform1i(U.uHasTex, 0);
    gl.bindVertexArray(p.vao);
    if (p.type) gl.drawElements(gl.TRIANGLES, p.count, p.type, 0); else gl.drawArrays(gl.TRIANGLES, 0, p.count);
  };
  gl.disable(gl.BLEND); gl.depthMask(true);
  const solid = (p) => p.mode < 2 || isArt(p) || (ov.logoWhite && p.nodeName === 'console_logo');
  for (const p of sc.prims) if (solid(p)) one(p);
  // see-through parts, far to near
  const blend = sc.prims.filter(p => !solid(p)).map(p => ({ p, z: M4.apply(M4.mul(mv0, p.world), p.centroid)[2] })).sort((a, b) => a.z - b.z);
  gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA); gl.depthMask(false);
  for (const b of blend) {
    if (b.p.mode === 3) {
      gl.blendFuncSeparate(gl.ZERO, gl.SRC_COLOR, gl.ZERO, gl.SRC_ALPHA); gl.uniform1i(U.uPass, 0); one(b.p);
      gl.blendFunc(gl.ONE, gl.ONE); gl.uniform1i(U.uPass, 1); one(b.p);
      gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
    } else one(b.p);
  }
  gl.depthMask(true); gl.disable(gl.BLEND);
  gl.bindFramebuffer(gl.READ_FRAMEBUFFER, fbMS); gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, fbRes);
  gl.blitFramebuffer(0, 0, size, size, 0, 0, size, size, gl.COLOR_BUFFER_BIT, gl.NEAREST);
  gl.bindFramebuffer(gl.FRAMEBUFFER, fbRes);
  const px = new Uint8Array(size * size * 4); gl.readPixels(0, 0, size, size, gl.RGBA, gl.UNSIGNED_BYTE, px);
  return px;
}
function b64(u8) { let s = ''; const CH = 0x8000; for (let i = 0; i < u8.length; i += CH) s += String.fromCharCode.apply(null, u8.subarray(i, i + CH)); return btoa(s); }

let SC = null, SCURL = null;
// One model at one angle: the render with the front art black (p0) and white (p1), and the front art's corners in pixels.
window.renderAngle = async function (url, opts) {
  if (SCURL !== url) { SC = await loadScene(url); SCURL = url; }
  const sc = SC; const size = opts.size;
  targets(size);
  const c = [(sc.lo[0] + sc.hi[0]) / 2, (sc.lo[1] + sc.hi[1]) / 2, (sc.lo[2] + sc.hi[2]) / 2];
  const r = Math.hypot(sc.hi[0] - sc.lo[0], sc.hi[1] - sc.lo[1], sc.hi[2] - sc.lo[2]) / 2;
  const fov = opts.fov || 20;
  const dist = r / Math.sin(fov * Math.PI / 360) * 1.02;
  const mv0 = M4.mul(M4.trans(0, 0, -dist), M4.mul(M4.rotX(opts.pitch), M4.mul(M4.rotY(opts.yaw), M4.trans(-c[0], -c[1], -c[2]))));
  const P = M4.persp(fov, 1, dist * 0.2, dist * 3);
  const pre = opts.artPrefix || ['front_art'];
  const mvp = M4.mul(P, mv0);
  const quadOf = (b) => { if (!b) return null; const [lo, hi] = b; const z = (lo[2] + hi[2]) / 2;
    const pts = [[lo[0], hi[1], z], [hi[0], hi[1], z], [hi[0], lo[1], z], [lo[0], lo[1], z]];
    const q = []; for (const p of pts) { const n = M4.apply(mvp, p); q.push((n[0] * 0.5 + 0.5) * size, (1 - (n[1] * 0.5 + 0.5)) * size); } return q; };
  const passes = opts.passes || [{ art: 0 }, { art: opts.white || 0.9 }];
  const out = passes.map(ps => b64(draw(sc, mv0, P, ps.art, pre, opts.expo, size, ps)));
  // art corners TL TR BR BL (image's top = +Y), in pixels (y down)
  const tp = sc.prims.find(p => p.mat === 'translucent_tinted_polycarbonate');
  const mats = {}; for (const p of sc.prims) if (p.mat) mats[p.mat] = Array.from(p.base);
  return { px: out, p0: out[0], p1: out[1], quad: quadOf(opts.art), logo: quadOf(opts.logo), size, trans: tp ? tp.trans : 0, tbase: tp ? tp.base : null, mats };
};
window.ready = true;
