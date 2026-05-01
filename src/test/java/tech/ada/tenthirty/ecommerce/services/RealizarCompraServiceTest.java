package tech.ada.tenthirty.ecommerce.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.ada.tenthirty.ecommerce.exception.NotFoundException;
import tech.ada.tenthirty.ecommerce.model.Compra;
import tech.ada.tenthirty.ecommerce.model.Item;
import tech.ada.tenthirty.ecommerce.model.StatusCompra;
import tech.ada.tenthirty.ecommerce.payload.CompraRequest;
import tech.ada.tenthirty.ecommerce.payload.response.CompraResponse;
import tech.ada.tenthirty.ecommerce.queue.ReservarItemEstoqueProducer;
import tech.ada.tenthirty.ecommerce.queue.payload.ReservarEstoqueRequest;
import tech.ada.tenthirty.ecommerce.repository.CompraRepository;
import tech.ada.tenthirty.ecommerce.repository.ItemRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealizarCompraServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private NotificarClienteService notificarClienteService;

    @Mock
    private ReservarItemEstoqueProducer reservarItemEstoqueProducer;

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private ModificarStatusCompraService modificarStatusCompraService;

    private RealizarCompraService realizarCompraService;

    @BeforeEach
    void setUp() {
        realizarCompraService = new RealizarCompraService(
                itemRepository,
                notificarClienteService,
                reservarItemEstoqueProducer,
                compraRepository,
                modificarStatusCompraService
        );
    }

    @Test
    void deveRealizarCompraComSucesso() {
        String compraId = UUID.randomUUID().toString();

        CompraRequest request = new CompraRequest();
        request.setCompraId(compraId);

        Compra compra = new Compra();
        compra.setId(1L);
        compra.setIdentificador(compraId);

        Item item = new Item();
        item.setSku("SKU-001");
        item.setQuantidadeUnidade(2);
        item.setValorUnitario(BigDecimal.valueOf(50.0));

        when(compraRepository.findByIdentificador(compraId))
                .thenReturn(Optional.of(compra));

        when(itemRepository.findByCompraId(compra.getId()))
                .thenReturn(List.of(item));

        CompraResponse response = realizarCompraService.realizarCompra(request);

        assertNotNull(response);
        assertEquals(compraId, response.getId());
        assertEquals(1, response.getItens().size());
        assertEquals("SKU-001", response.getItens().get(0).getSkuId());
        assertEquals(2, response.getItens().get(0).getQuantidade());
        assertEquals(50.0, response.getItens().get(0).getValorUnitario());

        verify(modificarStatusCompraService).execute(compra, StatusCompra.PENDENTE);
        verify(notificarClienteService).enviarConfirmacaoCompraCliente(compraId);
        verify(reservarItemEstoqueProducer).publish(any(ReservarEstoqueRequest.class));
    }

    @Test
    void deveLancarNotFoundExceptionQuandoCompraNaoExistir() {
        String compraId = UUID.randomUUID().toString();

        CompraRequest request = new CompraRequest();
        request.setCompraId(compraId);

        when(compraRepository.findByIdentificador(compraId))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> {
            realizarCompraService.realizarCompra(request);
        });

        verify(itemRepository, never()).findByCompraId(any());
        verify(reservarItemEstoqueProducer, never()).publish(any());
        verify(modificarStatusCompraService, never()).execute(any(), any());
        verify(notificarClienteService, never()).enviarConfirmacaoCompraCliente(any());
    }

    @Test
    void deveEnviarDadosCorretosParaReservaDeEstoque() {
        String compraId = UUID.randomUUID().toString();

        CompraRequest request = new CompraRequest();
        request.setCompraId(compraId);

        Compra compra = new Compra();
        compra.setId(1L);
        compra.setIdentificador(compraId);

        Item item = new Item();
        item.setSku("SKU-ABC");
        item.setQuantidadeUnidade(3);
        item.setValorUnitario(BigDecimal.valueOf(25.0));

        when(compraRepository.findByIdentificador(compraId))
                .thenReturn(Optional.of(compra));

        when(itemRepository.findByCompraId(compra.getId()))
                .thenReturn(List.of(item));

        realizarCompraService.realizarCompra(request);

        ArgumentCaptor<ReservarEstoqueRequest> captor =
                ArgumentCaptor.forClass(ReservarEstoqueRequest.class);

        verify(reservarItemEstoqueProducer).publish(captor.capture());

        ReservarEstoqueRequest requestEnviado = captor.getValue();

        assertEquals(compraId, requestEnviado.getCompraId());
        assertEquals(1, requestEnviado.getItems().size());
        assertEquals("SKU-ABC", requestEnviado.getItems().get(0).getSkuId());
        assertEquals(3, requestEnviado.getItems().get(0).getQuantidade());
        assertEquals(25.0, requestEnviado.getItems().get(0).getValorUnitario());
    }
}
