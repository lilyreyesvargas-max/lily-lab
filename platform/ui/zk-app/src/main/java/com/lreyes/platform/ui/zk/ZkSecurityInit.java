package com.lreyes.platform.ui.zk;

import com.lreyes.platform.ui.zk.model.UiUser;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.util.ExecutionInit;

/**
 * Listener ZK que se ejecuta al inicio de cada ejecución (request).
 * Si la página solicitada no es pública y el usuario no está autenticado,
 * redirige al login antes de que se cargue el ZUL.
 */
public class ZkSecurityInit implements ExecutionInit {

    private static final String[] PUBLIC_PATHS = {
        "/zul/login", "/zul/login.zul", "/", "/index.zul"
    };

    @Override
    public void init(Execution exec, Execution parent) {
        if (parent != null) return; // saltar ejecuciones AJAX hijas

        String path = exec.getDesktop().getRequestPath();
        if (isPublic(path)) return;

        UiUser user = (UiUser) exec.getSession().getAttribute("user");
        if (user == null) {
            exec.sendRedirect("/zul/login.zul");
        }
    }

    private boolean isPublic(String path) {
        for (String p : PUBLIC_PATHS) {
            if (path.equals(p) || path.startsWith(p)) return true;
        }
        return false;
    }
}
