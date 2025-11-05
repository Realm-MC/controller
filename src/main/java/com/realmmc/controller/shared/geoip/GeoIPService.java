package com.realmmc.controller.shared.geoip;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GeoIPService {

    private final Logger logger;
    private DatabaseReader reader;

    public GeoIPService(File dataFolder, Logger logger) {
        this.logger = logger;
        File database = new File(dataFolder, "GeoLite2-Country.mmdb");

        if (!database.exists()) {
            logger.severe("############################################################");
            logger.severe("### Base de dados GeoLite2-Country.mmdb não encontrada! ###");
            logger.severe("### Faça o download em https://dev.maxmind.com/geoip/    ###");
            logger.severe("### e coloque-a em: " + database.getAbsolutePath() + " ###");
            logger.severe("### A detecção de país por IP estará desativada.       ###");
            logger.severe("############################################################");
            this.reader = null;
        } else {
            try {
                this.reader = new DatabaseReader.Builder(database)
                        .withCache(new CHMCache())
                        .build();
                logger.info("Base de dados GeoIP carregada com sucesso de: " + database.getName());
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Erro ao carregar a base de dados GeoIP!", e);
                this.reader = null;
            }
        }
    }

    /**
     * Obtém o código ISO do país (ex: "BR", "US") para um dado endereço IP.
     * @param ipAddress O endereço IP a consultar.
     * @return Optional contendo o código do país se encontrado, ou Optional.empty() se não for encontrado ou se o serviço estiver inativo.
     */
    public Optional<String> getCountryCode(InetAddress ipAddress) {
        if (reader == null || ipAddress == null) {
            return Optional.empty();
        }
        if (ipAddress.isLoopbackAddress() || ipAddress.isSiteLocalAddress()) {
            return Optional.empty();
        }

        try {
            CountryResponse response = reader.country(ipAddress);
            return Optional.ofNullable(response.getCountry().getIsoCode());
        } catch (IOException | GeoIp2Exception e) {
            return Optional.empty();
        }
    }

    public void close() {
        if (reader != null) {
            try {
                reader.close();
                logger.info("Base de dados GeoIP fechada.");
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Erro ao fechar a base de dados GeoIP!", e);
            }
        }
    }
}