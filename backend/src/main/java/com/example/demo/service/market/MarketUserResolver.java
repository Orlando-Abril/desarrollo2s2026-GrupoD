package com.example.demo.service.market;

import com.example.demo.exception.OrderAccessDeniedException;
import com.example.demo.repository.market.MarketUserRepository;
import org.springframework.stereotype.Component;

/**
 * Resuelve el usuario autenticado para operar en el mercado (spec 027, research D8). Usa una proyección y nunca
 * carga la entidad {@code User}: la carga {@link MarketLocks#lockUsers} con el lock, y si ya estuviera en el
 * contexto de persistencia el lock devolvería un saldo viejo (research D3). Compara con el username configurado
 * del superusuario, no con su id, para funcionar aunque el superusuario no exista.
 */
@Component
public class MarketUserResolver {

    private final MarketUserRepository userRepository;
    private final SuperuserAccount superuserAccount;

    public MarketUserResolver(MarketUserRepository userRepository, SuperuserAccount superuserAccount) {
        this.userRepository = userRepository;
        this.superuserAccount = superuserAccount;
    }

    public MarketPrincipal resolve(String principalName) {
        if (superuserAccount.username().equals(principalName)) {
            throw new OrderAccessDeniedException(OrderAccessDeniedException.SUPERUSER_NOT_ALLOWED,
                    "El superusuario no opera por los endpoints de órdenes");
        }
        return userRepository.findPrincipalByUsername(principalName)
                .orElseThrow(() -> new OrderAccessDeniedException(OrderAccessDeniedException.USER_REQUIRED,
                        "La credencial no pertenece a un usuario"));
    }
}
