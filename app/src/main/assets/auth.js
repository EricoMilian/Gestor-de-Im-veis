import { initializeApp } from "https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  browserLocalPersistence,
  browserPopupRedirectResolver,
  setPersistence,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  onAuthStateChanged,
  signOut
} from "https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js";
import { firebaseConfig as localConfig } from "./firebase-config.js";

const AUTH_STORAGE_KEY = "gestao_imoveis_auth_v2";
const BIO_CRED_KEY = "gestao_imoveis_biometric_credential_v1";
let auth = null;
let firebaseReady = false;
let redirectChecked = false;

function el(id){ return document.getElementById(id); }
function status(msg){ const x=el("authStatus"); if(x) x.textContent=msg||""; }
function showLogin(show=true){ const x=el("telaLogin"); if(x) x.style.display=show?"flex":"none"; }
function setBusy(busy){
  ["btnGoogleLogin","btnBioLogin","btnAtivarBio"].forEach(id=>{ const x=el(id); if(x) x.disabled=busy; });
}
function isSecureForBiometric(){
  return window.isSecureContext && !!window.PublicKeyCredential && !!navigator.credentials;
}
function bytesToB64(bytes){ return btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/g,""); }
function b64ToBytes(s){ const p=s.replace(/-/g,"+").replace(/_/g,"/"); const pad="=".repeat((4-p.length%4)%4); const bin=atob(p+pad); return Uint8Array.from(bin,c=>c.charCodeAt(0)); }
function randomBytes(n=32){ return crypto.getRandomValues(new Uint8Array(n)); }
function getStoredBio(){ try{return JSON.parse(localStorage.getItem(BIO_CRED_KEY)||"null")}catch{return null;} }
function saveAuthMeta(user){
  if(!user) return;
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({uid:user.uid,email:user.email||"",name:user.displayName||""}));
}
function clearAuthMeta(){ localStorage.removeItem(AUTH_STORAGE_KEY); }

async function loadFirebaseConfig(){
  // Firebase Hosting exposes this endpoint automatically. This avoids hard-coding
  // the project ID in the HTML when the project is deployed to Firebase Hosting.
  try{
    const r=await fetch("/__/firebase/init.json",{cache:"no-store"});
    if(r.ok){
      const c=await r.json();
      if(c && c.apiKey && c.projectId && c.appId) return c;
    }
  }catch{}
  if(localConfig.apiKey && localConfig.projectId && localConfig.appId) return localConfig;
  return null;
}

async function initFirebase(){
  const config=await loadFirebaseConfig();
  if(!config){
    showLogin(true);
    status("Firebase ainda não foi configurado. Publique pelo Firebase Hosting ou preencha firebase-config.js.");
    return;
  }
  try{
    const app=initializeApp(config);
    auth=getAuth(app);
    await setPersistence(auth,browserLocalPersistence);
    firebaseReady=true;

    onAuthStateChanged(auth, async user=>{
      if(user){
        saveAuthMeta(user);
        showLogin(false);
        status("");
        if(typeof window.render === "function") window.render();
        atualizarBotoesBiometria(user);
      }else{
        showLogin(true);
        atualizarBotoesBiometria(null);
      }
    });

    if(!redirectChecked){
      redirectChecked=true;
      try{ await getRedirectResult(auth); }catch(e){ exibirErroFirebase(e); }
    }
  }catch(e){
    console.error(e);
    showLogin(true);
    status("Não foi possível iniciar o Firebase. Verifique a configuração.");
  }
}

function exibirErroFirebase(e){
  console.error(e);
  const code=e?.code||"";
  if(code.includes("popup-blocked")) status("O navegador bloqueou a janela. Toque novamente em Entrar com Google.");
  else if(code.includes("unauthorized-domain")) status("Este endereço ainda não está autorizado no Firebase Authentication.");
  else if(code.includes("popup-closed-by-user")) status("Login cancelado.");
  else status("Não foi possível concluir o login com Google. Tente novamente.");
}

async function entrarComGoogle(){
  if(!firebaseReady){ status("Firebase não está pronto."); return; }
  setBusy(true); status("Abrindo sua conta Google...");
  const provider=new GoogleAuthProvider();
  provider.setCustomParameters({prompt:"select_account"});
  try{
    await signInWithPopup(auth,provider,browserPopupRedirectResolver);
  }catch(e){
    // On mobile, a popup may be blocked. Redirect is the reliable fallback.
    if(e?.code==="auth/popup-blocked" || e?.code==="auth/operation-not-supported-in-this-environment"){
      try{ await signInWithRedirect(auth,provider,browserPopupRedirectResolver); return; }catch(err){ exibirErroFirebase(err); }
    }else if(e?.code!=="auth/popup-closed-by-user") exibirErroFirebase(e);
  }finally{ setBusy(false); }
}

async function ativarBiometria(){
  if(!firebaseReady || !auth?.currentUser){ status("Entre com Google primeiro."); return; }
  if(!isSecureForBiometric()){
    status("A biometria exige HTTPS. Não funciona abrindo index.html diretamente como arquivo.");
    return;
  }
  setBusy(true); status("Confirme sua impressão digital ou identificação facial...");
  try{
    const user=auth.currentUser;
    const challenge=randomBytes(32);
    const userId=randomBytes(16);
    const credential=await navigator.credentials.create({publicKey:{
      challenge,
      rp:{name:"Gestão de Imóveis",id:location.hostname},
      user:{id:userId,name:user.email||user.uid,displayName:user.displayName||"Usuário"},
      pubKeyCredParams:[{type:"public-key",alg:-7},{type:"public-key",alg:-257}],
      authenticatorSelection:{authenticatorAttachment:"platform",residentKey:"required",requireResidentKey:true,userVerification:"required"},
      timeout:60000,attestation:"none"
    }});
    if(!credential) throw new Error("credential-null");
    localStorage.setItem(BIO_CRED_KEY,JSON.stringify({
      credentialId:bytesToB64(credential.rawId),uid:user.uid,email:user.email||"",createdAt:new Date().toISOString()
    }));
    status("Biometria ativada neste celular.");
    atualizarBotoesBiometria(user);
  }catch(e){
    console.error(e);
    if(e?.name==="NotAllowedError") status("A operação foi cancelada ou o celular não autorizou a biometria.");
    else status("Não foi possível ativar a biometria neste dispositivo.");
  }finally{ setBusy(false); }
}

async function entrarComBiometria(){
  const saved=getStoredBio();
  if(!saved){ status("Este botão está pronto para usar a biometria/Face do celular. Primeiro vincule este aparelho à sua conta Google pelo botão acima e ative a biometria."); return; }
  if(!isSecureForBiometric()){ status("A biometria exige HTTPS. Não funciona abrindo index.html diretamente como arquivo."); return; }
  if(!firebaseReady || !auth?.currentUser){
    status("Para usar a biometria como desbloqueio, a sessão do Google/Firebase precisa continuar vinculada a este aparelho. Entre com Google uma vez.");
    return;
  }
  if(auth.currentUser.uid!==saved.uid){ status("Esta biometria está vinculada a outra conta Google neste aparelho."); return; }
  setBusy(true); status("Confirme sua impressão digital ou identificação facial...");
  try{
    const assertion=await navigator.credentials.get({publicKey:{
      challenge:randomBytes(32),
      rpId:location.hostname,
      allowCredentials:[{type:"public-key",id:b64ToBytes(saved.credentialId),transports:["internal"]}],
      userVerification:"required",timeout:60000
    }});
    if(!assertion) throw new Error("assertion-null");
    showLogin(false);
    if(typeof window.render === "function") window.render();
    status("");
  }catch(e){
    console.error(e);
    if(e?.name==="NotAllowedError") status("Biometria cancelada ou não reconhecida.");
    else status("Não foi possível validar a biometria.");
  }finally{ setBusy(false); }
}

function atualizarBotoesBiometria(user){
  const saved=getStoredBio();
  const bio=el("btnBioLogin");
  const ativar=el("btnAtivarBio");
  if(bio) bio.style.display="block";
  // The activation button is shown only while the user is authenticated but the
  // login card is visible. The app can also expose it from the browser console.
  if(ativar) ativar.style.display=user && !saved && isSecureForBiometric() ? "block":"none";
}

function bloquearApp(){
  showLogin(true);
  const saved=getStoredBio();
  const user=auth?.currentUser;
  atualizarBotoesBiometria(user);
  if(saved && isSecureForBiometric()) status("Aplicativo bloqueado. Use sua biometria/Face para entrar.");
  else status("Aplicativo bloqueado. Use sua conta Google para entrar.");
}

// Compatibilidade com o botão antigo do aplicativo.
window.entrarComGoogle=entrarComGoogle;
window.ativarBiometria=ativarBiometria;
window.entrarComBiometria=entrarComBiometria;
window.bloquearApp=bloquearApp;
window.fazerLogout=bloquearApp;
window.fazerLoginBiometrico=entrarComBiometria;

window.firebaseAuthState=()=>auth?.currentUser||null;

window.addEventListener("DOMContentLoaded",()=>{
  showLogin(true);
  initFirebase();
});
