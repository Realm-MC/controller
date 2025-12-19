package com.palacesky.controller.modules.server.data;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ScalingRules {
    /** Mínimo de servidores ONLINE e NÃO CHEIOS para manter. */
    private int minIdle = 1;

    /** Máximo absoluto de servidores desse tipo permitidos. */
    private int maxTotal = 7;

    /** Porcentagem para considerar o servidor "cheio". */
    private double fullThreshold = 0.75;

    /** Tempo em segundos que o servidor deve ficar com 0 jogadores antes de desligar. */
    private long emptyShutdownSeconds = 60;
}