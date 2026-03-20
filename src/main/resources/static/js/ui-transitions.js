(function(){
  function main(){
    // Minimal, robust UI transitions + AJAX form/link intercepts
    const css = `
.auth-wrapper { transition: opacity .25s ease, transform .25s ease; opacity:0; transform: translateY(8px); }
.auth-wrapper.show { opacity:1; transform: translateY(0); }
.auth-wrapper.fade-out { opacity:0; transform: translateY(-8px); }
.field-error { color:#fff; background:#c0392b; padding:8px 10px; border-radius:6px; font-size:13px; margin-top:8px; display:inline-block; }
.btn-disabled { opacity:0.6; pointer-events:none; }
`;
    const style = document.createElement('style'); style.textContent = css; document.head.appendChild(style);

    function replaceAuthContentFromHTML(htmlText, url, push){
      try{
        const parser = new DOMParser();
        const doc = parser.parseFromString(htmlText, 'text/html');
        // carry over page-specific inline <style> (templates have important css blocks)
        const newHeadStyle = doc.head ? doc.head.querySelector('style') : null;
        const existingAuthStyle = document.head.querySelector('style[data-auth-style]');
        if(existingAuthStyle) existingAuthStyle.remove();
        if(newHeadStyle && newHeadStyle.textContent){
          const cloned = document.createElement('style');
          cloned.setAttribute('data-auth-style', '1');
          cloned.textContent = newHeadStyle.textContent;
          document.head.appendChild(cloned);
        }
        const newWrapper = doc.querySelector('.auth-wrapper');
        if(newWrapper){
          const old = document.querySelector('.auth-wrapper');
          if(!old){ console.warn('No .auth-wrapper found; doing safe navigation to:', url); window.location = url; return; }
          old.classList.remove('show'); old.classList.add('fade-out');
          setTimeout(()=>{ old.outerHTML = newWrapper.outerHTML; initAuth(); if(push) history.pushState({ajax:true}, '', url); }, 120);
        } else {
          // If server returned a full HTML page (e.g., login success redirects to home HTML),
          // replace the whole document with the returned HTML instead of navigating to the POST URL (which would issue a GET and may be 405).
          if(doc && doc.documentElement && doc.body && doc.body.innerHTML.trim().length > 0){
            console.debug('AJAX response contains full HTML – replacing document without making a GET to', url);
            document.open(); document.write(htmlText); document.close();
            return;
          }
          window.location = url;
        }
      }catch(e){ console.error(e); window.location = url; }
    }

    function loadAuthPage(url, push=true){
      fetch(url, { credentials:'same-origin' })
        .then(r=>r.text().then(t=>({ok:r.ok,status:r.status,text:t})))
        .then(res=>{ if(!res.ok){ showServerError(res.status, res.text); return; } replaceAuthContentFromHTML(res.text, url, push); })
        .catch(()=> window.location = url);
    }
    // expose for inline handlers in templates (some use onclick="loadAuthPage(...)")
    window.loadAuthPage = loadAuthPage;
    window.replaceAuthContentFromHTML = replaceAuthContentFromHTML;

    function showServerError(status, html){ const w=document.querySelector('.auth-wrapper'); if(!w){ alert('Server error: '+status); console.error(html); return; } let b=w.querySelector('.server-error-box'); if(!b){ b=document.createElement('div'); b.className='server-error-box'; b.style.margin='12px'; b.style.padding='12px'; b.style.background='#8b1f1f'; b.style.color='#fff'; b.style.borderRadius='6px'; w.insertBefore(b, w.firstChild);} b.textContent='Server error (HTTP '+status+'). Please try again.'; console.error('Server HTML response:', html); }


    window.addEventListener('popstate', function(e){ loadAuthPage(window.location.pathname + window.location.search, false); });

    initAuth();
  }

  if(document.readyState==='loading'){ document.addEventListener('DOMContentLoaded', main); }else{ main(); }
})();


    // Toggle password visibility (exposed globally so templates can call it)
    window.togglePass = function() {
      const x = document.getElementById('password');
      const icon = document.getElementById('eyeIcon');
      if (!x) return;
      if (x.type === 'password') { x.type='text'; if(icon){ icon.classList.remove('fa-eye-slash'); icon.classList.add('fa-eye'); } } else { x.type='password'; if(icon){ icon.classList.remove('fa-eye'); icon.classList.add('fa-eye-slash'); } }
    };

    window.toggleRegPass = function(inputId, iconId) {
      const input = document.getElementById(inputId);
      const icon = document.getElementById(iconId);
      if (!input) return;
      if (input.type === 'password') { input.type='text'; if(icon){ icon.classList.remove('fa-eye-slash'); icon.classList.add('fa-eye'); } } else { input.type='password'; if(icon){ icon.classList.remove('fa-eye'); icon.classList.add('fa-eye-slash'); } }
    };

    // Intercept auth links to do AJAX navigation
    function interceptLinks(){
      document.querySelectorAll('a[href]').forEach(a => {
        const href = a.getAttribute('href');
        if(!href || href.startsWith('#') || href.startsWith('javascript:') || href.startsWith('mailto:')) return;
        if (href.indexOf('/acc/reg') !== -1 || href.indexOf('/acc/log') !== -1 || href.indexOf('/acc/forgot') !== -1) {
          a.addEventListener('click', function(e){
            e.preventDefault();
            loadAuthPage(href, true);
          });
        }
      });
    }

    // Intercept forms inside .auth-wrapper to submit via AJAX and replace content
    function interceptForms(){
      const forms = document.querySelectorAll('.auth-wrapper form');
      forms.forEach(form => {
        if(form.__ajaxBound) return; // prevent double-bind
        form.__ajaxBound = true;
        form.addEventListener('submit', function(e){
          e.preventDefault();
          ajaxSubmitForm(form);
        });
      });
    }

    // Generic AJAX submit handler shared by interceptForms and delegated listener
    function ajaxSubmitForm(form){
      const action = form.action || window.location.pathname;
      const method = (form.method || 'post').toLowerCase();
      const fd = new FormData(form);
      const opts = {
        method: method.toUpperCase(),
        body: method === 'get' ? null : fd,
        credentials: 'same-origin',
        headers: {
          'X-Requested-With': 'XMLHttpRequest'
        }
      };
      // For GET forms, build query string
      let finalUrl = action;
      if(method === 'get'){
        const params = new URLSearchParams(fd);
        finalUrl = action + (action.indexOf('?') === -1 ? '?' : '&') + params.toString();
      }
      const submitBtn = form.querySelector('button[type="submit"]'); if(submitBtn){ submitBtn.disabled=true; submitBtn.dataset._orig=submitBtn.innerHTML; submitBtn.innerHTML='Processing...'; submitBtn.classList.add('btn-disabled'); }
      console.debug('[AJAX submit] method=', method, 'url=', finalUrl);
      fetch(finalUrl, opts).then(r=>r.text().then(t=>({ok:r.ok,status:r.status,text:t}))).then(res=>{ if(!res.ok){ if(submitBtn){ try{ submitBtn.disabled=false; submitBtn.innerHTML=submitBtn.dataset._orig||submitBtn.innerHTML; submitBtn.classList.remove('btn-disabled'); }catch(e){} } showServerError(res.status, res.text); return; } if(submitBtn){ try{ submitBtn.disabled=false; submitBtn.innerHTML=submitBtn.dataset._orig||submitBtn.innerHTML; submitBtn.classList.remove('btn-disabled'); }catch(e){} }
        const pushHistory = (method === 'get');
        console.debug('[AJAX submit] response ok, pushHistory=', pushHistory);
        replaceAuthContentFromHTML(res.text, finalUrl, pushHistory);
      }).catch(err=>{ console.error('AJAX form submit failed', err); if(submitBtn){ try{ submitBtn.disabled=false; submitBtn.innerHTML=submitBtn.dataset._orig||submitBtn.innerHTML; submitBtn.classList.remove('btn-disabled'); }catch(e){} } try{ form.submit(); }catch(e){ window.location = finalUrl; } });
    }

    // Delegated submit listener: catches any submit from forms placed inside .auth-wrapper (works with dynamic content)
    document.addEventListener('submit', function(e){
      const form = e.target;
      if(!(form instanceof HTMLFormElement)) return;
      if(!form.closest || !form.closest('.auth-wrapper')) return; // only handle auth forms
      if(form.__ajaxBound) return; // already individually bound
      e.preventDefault();
      ajaxSubmitForm(form);
    });

    // Generic client-side validation for two-password forms (register, reset)
    function bindRegisterValidation(){
      const forms = document.querySelectorAll('.auth-wrapper form');
      forms.forEach(regForm => {
        if(regForm.__pwValidationBound) return;
        const pass1 = regForm.querySelector('#pass1');
        const pass2 = regForm.querySelector('#pass2');
        const phone = regForm.querySelector('#phone');
        const submit = regForm.querySelector('button[type="submit"]');
        if(!pass1 || !pass2 || !submit) return;
        regForm.__pwValidationBound = true;

        let errEl = null;
        let touched = false;

        function showError(msg){
          if(!errEl){ errEl = document.createElement('div'); errEl.className='field-error'; regForm.insertBefore(errEl, submit); }
          errEl.textContent = msg;
          submit.classList.add('btn-disabled');
        }
        function clearError(){ if(errEl) errEl.remove(); errEl = null; submit.classList.remove('btn-disabled'); }

        function validate(showErrors){
          const p1 = pass1.value || '';
          const p2 = pass2.value || '';
          const ph = phone ? (phone.value || '') : '';

          if(!showErrors && p1.length === 0 && p2.length === 0 && ph.length === 0){ clearError(); return true; }
          // only validate phone if the phone input exists (reset form doesn't have phone)
          if(phone){
            if(ph.length === 0){ showError('Phone number is required'); return false; }
            // simple digits check
            if(!/^\d{9,12}$/.test(ph)) { showError('Invalid phone number (9-12 digits)'); return false; }
          }
          if(p1.length < 6){ showError('Password must be at least 6 characters'); return false; }
          if(p1 !== p2){ showError('Passwords do not match'); return false; }
          clearError(); return true;
        }

        const onInput = function(){ if(touched){ validate(true); } };
        const onBlur = function(){ if(touched){ validate(true); } };

        pass1.addEventListener('input', onInput);
        pass2.addEventListener('input', onInput);
        if(phone) { phone.addEventListener('input', onInput); phone.addEventListener('blur', onBlur); }
        pass1.addEventListener('blur', onBlur);
        pass2.addEventListener('blur', onBlur);

        regForm.addEventListener('submit', function(e){
          touched = true; // only show validation after user attempts submit
          const ok = validate(true);
          if(!ok){ e.preventDefault(); errEl && errEl.focus && errEl.focus(); }
          return ok;
        });
      });
    }

    function removeActiveNav(){
      document.querySelectorAll('.header-area .nav li.active, .header-area .nav a.active').forEach(i => i.classList.remove('active'));
    }

    // Initialize bindings for current content
    function initAuth(){
      // ensure wrapper exists
      const wrapper = document.querySelector('.auth-wrapper');
      if(wrapper){
        // clear any lingering fade-out and show the wrapper so it doesn't stay invisible
        wrapper.classList.remove('fade-out');
        wrapper.classList.add('show');
      }
      // Remove leftover active nav state (keeps nav consistent)
      removeActiveNav();
      // update inline style from existing page head (if present)
      const headStyle = document.querySelector('head style');
      if(headStyle && headStyle.textContent){ /* keep existing */ }
      // Re-bind links and forms
      interceptLinks();
      interceptForms();
      bindRegisterValidation();
    }

    // handle back/forward
    window.addEventListener('popstate', function(e){
      const url = window.location.pathname + window.location.search;
      loadAuthPage(url, false);
    });

