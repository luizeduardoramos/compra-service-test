package tech.ada.tenthirty.ecommerce.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import tech.ada.tenthirty.ecommerce.queue.payload.ReservarEstoqueRequest;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservarItemEstoqueProducer {


    private final ObjectMapper objectMapper;
    private final StreamBridge streamBridge;
    private final RabbitTemplate rabbitTemplate;
    private final Queue queue;
    @Value("${negocio.estoque.fila.reservar.out}")
    private String fila;

    private String convert(ReservarEstoqueRequest reservarEstoque) {
        try {
            return objectMapper.writeValueAsString(reservarEstoque);
        } catch (JsonProcessingException e) {
            log.error("Error trying to convert object {}",e.getMessage(),e);
            return Strings.EMPTY;
        }
    }

   public void publish (ReservarEstoqueRequest reservarEstoqueRequest){
       String converted = convert(reservarEstoqueRequest);
       streamBridge.send("publish-out-0", reservarEstoqueRequest);
       //rabbitTemplate.convertAndSend(queue.getName(), converted);
       log.info("mensagem enviada com sucesso. {}", converted);
   }

}
