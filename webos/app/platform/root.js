/* Advanced edition: authorized READ-ONLY inspection of already-existing Root access.
   Does not root, flash, install any service, or intercept Home. */
(function(g){"use strict";g.TigrisRoot={
  id:"existing-root",
  inspect:function(){return g.TigrisCore.call("luna://org.webosbrew.hbchannel.service/exec",{command:"id -u"},6000).then(function(r){
    return{available:String(r.stdoutString||r.stdout||"").trim()==="0",configured:false};
  }).catch(function(){return{available:false,configured:false}})}
}})(window);
