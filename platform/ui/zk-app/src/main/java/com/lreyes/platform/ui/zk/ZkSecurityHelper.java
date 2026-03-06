package com.lreyes.platform.ui.zk;

import com.lreyes.platform.ui.zk.model.UiUser;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Sessions;

import java.util.Arrays;

/**
 * Helper de seguridad para ViewModels ZK.
 * <p>
 * Proporciona verificaciones server-side de autenticación y roles
 * que deben invocarse al inicio de cada {@code @Init} y en los
 * {@code @Command} de escritura que requieran privilegios.
 */
public final class ZkSecurityHelper {

    private ZkSecurityHelper() {}

    /**
     * Verifica que el usuario esté autenticado. Si no, redirige a login.
     *
     * @return el {@link UiUser} de sesión
     * @throws SecurityException si no hay sesión activa
     */
    public static UiUser requireAuthenticated() {
        UiUser user = (UiUser) Sessions.getCurrent().getAttribute("user");
        if (user == null) {
            Executions.sendRedirect("/zul/login.zul");
            throw new SecurityException("Sesión no activa");
        }
        return user;
    }

    /**
     * Verifica que el usuario autenticado tenga al menos uno de los roles indicados
     * o sea platform admin.
     *
     * @param roles roles aceptados (nombres sin prefijo ROLE_)
     * @return el {@link UiUser} de sesión
     * @throws SecurityException si no tiene el rol requerido
     */
    public static UiUser requireRole(String... roles) {
        UiUser user = requireAuthenticated();
        if (user.isPlatformAdmin()) {
            return user;
        }
        boolean hasRole = Arrays.stream(roles).anyMatch(user::hasRole);
        if (!hasRole) {
            throw new SecurityException(
                    "Acceso denegado: se requiere uno de los roles " + Arrays.toString(roles));
        }
        return user;
    }

    /**
     * Verifica que el usuario autenticado sea platform admin.
     *
     * @return el {@link UiUser} de sesión
     * @throws SecurityException si no es platform admin
     */
    public static UiUser requirePlatformAdmin() {
        UiUser user = requireAuthenticated();
        if (!user.isPlatformAdmin()) {
            throw new SecurityException("Acceso denegado: se requiere platform_admin");
        }
        return user;
    }
}
