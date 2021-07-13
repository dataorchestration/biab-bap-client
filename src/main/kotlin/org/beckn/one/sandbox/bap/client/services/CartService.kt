package org.beckn.one.sandbox.bap.client.services

import arrow.core.Either
import arrow.core.flatMap
import org.beckn.one.sandbox.bap.client.dtos.CartDto
import org.beckn.one.sandbox.bap.client.dtos.CartItemDto
import org.beckn.one.sandbox.bap.client.errors.validation.CartError
import org.beckn.one.sandbox.bap.client.mappers.SelectedItemMapper
import org.beckn.one.sandbox.bap.errors.HttpError
import org.beckn.one.sandbox.bap.message.entities.MessageDao
import org.beckn.one.sandbox.bap.message.services.MessageService
import org.beckn.one.sandbox.bap.schemas.ProtocolContext
import org.beckn.one.sandbox.bap.schemas.ProtocolLocation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class CartService @Autowired constructor(
  private val messageService: MessageService,
  private val registryService: RegistryService,
  private val bppService: BppService,
  private val selectedItemMapper: SelectedItemMapper,
  private val log: Logger = LoggerFactory.getLogger(CartService::class.java)
) {
  fun saveCart(context: ProtocolContext, cart: CartDto): Either<HttpError, MessageDao?> {
    log.info("Got save cart request. Context: {}, Cart: {}", context, cart)
    if (cart.items.isNullOrEmpty()) {
      log.info("Empty cart received, not doing anything. Cart: {}", cart)
      return Either.Right(null)
    }

    if (areMultipleBppItemsSelected(cart.items)) {
      log.info("Cart contains items from more than one BPP, returning error. Cart: {}", cart)
      return Either.Left(CartError.MultipleBpps)
    }

    if (areMultipleProviderItemsSelected(cart.items)) {
      log.info("Cart contains items from more than one provider, returning error. Cart: {}", cart)
      return Either.Left(CartError.MultipleProviders)
    }
    return registryService.lookupBppById(cart.items.first().bppId)
      .flatMap {
        bppService.select(
          context,
          bppUri = it.first().subscriber_url,
          providerId = cart.items.first().provider.id,
          providerLocation = ProtocolLocation(id = cart.items.first().provider.locations?.first()),
          items = cart.items.map { cartItem -> selectedItemMapper.dtoToProtocol(cartItem) }
        )
      }
      .flatMap {
        messageService.save(MessageDao(id = context.messageId, type = MessageDao.Type.Select))
      }
  }

  private fun areMultipleProviderItemsSelected(items: List<CartItemDto>) =
    items.distinctBy { it.provider.id }.size > 1

  private fun areMultipleBppItemsSelected(items: List<CartItemDto>) =
    items.distinctBy { it.bppId }.size > 1
}
