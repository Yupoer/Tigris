(function(g){"use strict";
function call(uri,args,timeout){return new Promise(function(ok,fail){
  if(typeof g.PalmServiceBridge!=="function")return fail(Error("webOS Luna not available"));
  var bridge=new g.PalmServiceBridge(),done=false;
  var timer=setTimeout(function(){if(!done){done=true;fail(Error("Luna timeout"));}},timeout||6500);
  bridge.onservicecallback=function(reply){if(done)return;var data;
    try{data=JSON.parse(reply)}catch(e){done=true;clearTimeout(timer);return fail(e)}
    if(data&&data.subscribed)return;done=true;clearTimeout(timer);
    if(data&&data.returnValue===false)return fail(Error(data.errorText||"Service denied"));ok(data);
  };
  try{bridge.call(uri,JSON.stringify(args||{}))}catch(e){done=true;clearTimeout(timer);fail(e)}
})}
function launch(app){if(!/^[a-z0-9_.-]{3,160}$/i.test(app||""))return Promise.reject(Error("Invalid app ID"));
return call("luna://com.webos.applicationManager/launch",{id:app},7000)}
function listApps(){return call("luna://com.webos.applicationManager/listLaunchPoints",{},8000).then(function(d){
return(d.launchPoints||[]).filter(function(a){return a&&a.id&&a.title&&a.id!=="org.tigris.webos"}).map(function(a){return{id:a.id,title:a.title,icon:a.icon||a.largeIcon||""}})})}
function listInputs(){return call("luna://com.webos.service.eim/getAllInputStatus",{},5000).then(function(r){
return(r.devices||[]).filter(function(d){return d.appId&&d.label}).map(function(d){return{id:d.appId,title:d.label,icon:""}})}).catch(function(){return[]})}
g.TigrisCore={call:call,launch:launch,listApps:listApps,listInputs:listInputs};
})(window);
