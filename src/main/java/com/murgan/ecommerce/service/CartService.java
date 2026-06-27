package com.murgan.ecommerce.service; // Declares the package for cart-related services.

import java.math.BigDecimal; // Imports decimal arithmetic for money values.

import org.springframework.stereotype.Service; // Marks this class as a Spring service bean.
import org.springframework.transaction.annotation.Transactional; // Wraps methods in database transactions.

import com.murgan.ecommerce.domain.Cart; // Imports the cart domain model.
import com.murgan.ecommerce.domain.CartItem; // Imports the cart item domain model.
import com.murgan.ecommerce.domain.Order; // Imports the order domain model.
import com.murgan.ecommerce.domain.OrderItem; // Imports the order item domain model.
import com.murgan.ecommerce.domain.Product; // Imports the product domain model.
import com.murgan.ecommerce.domain.User; // Imports the user domain model.
import com.murgan.ecommerce.repository.CartRepository; // Imports the cart repository.
import com.murgan.ecommerce.repository.OrderRepository; // Imports the order repository.
import com.murgan.ecommerce.repository.ProductRepository; // Imports the product repository.

@Service // Registers this class as a Spring-managed service.
public class CartService { // Defines the service that manages cart actions.

	private final CurrentUserService currentUserService; // Resolves the authenticated user's email.
	private final UserService userService; // Loads user records from persistence.
	private final CartRepository cartRepository; // Persists and loads carts.
	private final ProductRepository productRepository; // Loads products and locks stock rows.
	private final OrderRepository orderRepository; // Persists orders at checkout.

	public CartService( // Creates the service with its required dependencies.
		CurrentUserService currentUserService, // Injects the current-user helper.
		UserService userService, // Injects the user lookup service.
		CartRepository cartRepository, // Injects the cart repository.
		ProductRepository productRepository, // Injects the product repository.
		OrderRepository orderRepository // Injects the order repository.
	) { // Starts constructor body.
		this.currentUserService = currentUserService; // Stores the current-user helper.
		this.userService = userService; // Stores the user service.
		this.cartRepository = cartRepository; // Stores the cart repository.
		this.productRepository = productRepository; // Stores the product repository.
		this.orderRepository = orderRepository; // Stores the order repository.
	} // Ends constructor.

	@Transactional // Runs this lookup and lazy-create flow inside one transaction.
	public Cart getMyCart() { // Returns the current user's cart, creating one if needed.
		User user = userService.requireByEmail(currentUserService.requireEmail()); // Finds the authenticated user.
		return cartRepository.findWithItemsByUser(user).orElseGet(() -> { // Loads the cart or creates a new one.
			Cart c = new Cart(); // Creates a new cart instance.
			c.setUser(user); // Assigns the cart to the current user.
			return cartRepository.save(c); // Saves and returns the new cart.
		}); // Ends lazy cart creation.
	} // Ends getMyCart.

	@Transactional // Keeps cart mutation and persistence consistent.
	public Cart addItem(Long productId, int quantity) { // Adds a product to the current cart.
		if (quantity <= 0) { // Rejects invalid quantities.
			throw new IllegalArgumentException("Quantity must be > 0"); // Signals that quantity must be positive.
		} // Ends quantity validation.

		Cart cart = getMyCart(); // Loads the user's cart.
		Product product = productRepository.findById(productId).orElseThrow(() -> new NotFoundException("Product not found")); // Loads the product or fails.

		CartItem item = cart.getItems().stream() // Starts searching existing cart items.
			.filter(i -> i.getProduct().getId().equals(productId)) // Keeps only the matching product item.
			.findFirst() // Takes the first matching item if present.
			.orElseGet(() -> { // Creates a new cart item if none exists.
				CartItem ci = new CartItem(); // Creates a new cart item.
				ci.setCart(cart); // Links the item back to the cart.
				ci.setProduct(product); // Links the item to the product.
				ci.setQuantity(0); // Starts the quantity at zero before adding.
				ci.setLineTotal(BigDecimal.ZERO); // Initializes the line total to zero.
				cart.getItems().add(ci); // Adds the new item to the cart.
				return ci; // Returns the created item.
			}); // Ends item lookup or creation.

		item.setQuantity(item.getQuantity() + quantity); // Increases the quantity by the requested amount.
		item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))); // Recomputes the line total.
		recalc(cart); // Recomputes the cart total from all line totals.
		return cart; // Returns the updated cart.
	} // Ends addItem.

	@Transactional // Keeps item update and persistence consistent.
	public Cart updateItem(Long productId, int quantity) { // Updates a cart item quantity or removes it.
		Cart cart = getMyCart(); // Loads the user's cart.
		CartItem existing = cart.getItems().stream() // Searches the cart items.
			.filter(i -> i.getProduct().getId().equals(productId)) // Matches the requested product.
			.findFirst() // Picks the first matching item.
			.orElseThrow(() -> new NotFoundException("Item not in cart")); // Fails if the item is missing.

		if (quantity <= 0) { // Treats zero or negative quantity as removal.
			cart.getItems().remove(existing); // Removes the item from the cart.
		} else { // Handles the normal update path.
			existing.setQuantity(quantity); // Stores the new quantity.
			existing.setLineTotal(existing.getProduct().getPrice().multiply(BigDecimal.valueOf(quantity))); // Recalculates the line total.
		} // Ends quantity update/removal branch.
		recalc(cart); // Updates the cart total after the change.
		return cart; // Returns the updated cart.
	} // Ends updateItem.

	@Transactional // Keeps item removal and total recalculation atomic.
	public Cart removeItem(Long productId) { // Removes a product from the cart.
		Cart cart = getMyCart(); // Loads the user's cart.
		cart.getItems().removeIf(i -> i.getProduct().getId().equals(productId)); // Removes any matching item.
		recalc(cart); // Recomputes the cart total.
		return cart; // Returns the updated cart.
	} // Ends removeItem.

	@Transactional // Ensures checkout is all-or-nothing.
	public Order checkout(String shippingAddress) { // Converts the cart into an order.
		if (shippingAddress == null || shippingAddress.isBlank()) { // Validates the shipping address input.
			throw new IllegalArgumentException("Shipping address required"); // Rejects empty addresses.
		} // Ends address validation.

		Cart cart = getMyCart(); // Loads the current cart.
		if (cart.getItems().isEmpty()) { // Prevents checkout with no items.
			throw new IllegalArgumentException("Cart is empty"); // Signals that checkout requires items.
		} // Ends empty-cart check.

		Order order = new Order(); // Creates a new order.
		order.setUser(cart.getUser()); // Copies the cart owner onto the order.
		order.setShippingAddress(shippingAddress.trim()); // Stores the normalized shipping address.
		order.setStatus(Order.Status.PENDING); // Marks the order as pending.

		BigDecimal total = BigDecimal.ZERO; // Starts the order total at zero.

		for (CartItem ci : cart.getItems()) { // Processes each cart item.
			Product p = productRepository.findByIdForUpdate(ci.getProduct().getId()) // Locks the product row for stock updates.
				.orElseThrow(() -> new NotFoundException("Product not found")); // Fails if the product no longer exists.

			if (p.getStockQuantity() < ci.getQuantity()) { // Checks available stock.
				throw new IllegalArgumentException("Insufficient stock for product: " + p.getName()); // Rejects overselling.
			} // Ends stock validation.

			p.setStockQuantity(p.getStockQuantity() - ci.getQuantity()); // Decreases inventory by the ordered quantity.

			OrderItem oi = new OrderItem(); // Creates a new order item.
			oi.setOrder(order); // Links the item to the order.
			oi.setProduct(p); // Copies the product into the order item.
			oi.setQuantity(ci.getQuantity()); // Copies the quantity from the cart.
			oi.setLineTotal(p.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()))); // Computes the item total.

			order.getItems().add(oi); // Adds the order item to the order.
			total = total.add(oi.getLineTotal()); // Accumulates the order total.
		} // Ends cart-item loop.

		order.setTotal(total); // Stores the final order total.
		Order saved = orderRepository.save(order); // Persists the order and gets the saved entity.

		cart.getItems().clear(); // Empties the cart after successful checkout.
		cart.setTotal(BigDecimal.ZERO); // Resets the cart total to zero.

		return saved; // Returns the saved order.
	} // Ends checkout.
// Runs inside a transaction.
@Transactional
public Cart validateCheckout(String shippingAddress) {

    // Check whether the shipping address is provided.
    if (shippingAddress == null || shippingAddress.isBlank()) {
        throw new IllegalArgumentException("Shipping address required");
    }

    // Load the current user's cart.
    Cart cart = getMyCart();

    // Ensure the cart contains at least one item.
    if (cart.getItems().isEmpty()) {
        throw new IllegalArgumentException("Cart is empty");
    }

    // Loop through every item in the cart.
    for (CartItem ci : cart.getItems()) {

        // Lock the product row so that stock cannot change while validating.
        Product product = productRepository.findByIdForUpdate(ci.getProduct().getId())
                .orElseThrow(() -> new NotFoundException("Product not found"));

        // Check whether enough stock is available.
        if (product.getStockQuantity() < ci.getQuantity()) {
            throw new IllegalArgumentException(
                    "Insufficient stock for product: " + product.getName()
            );
        }
    }

    // Everything is valid.
    return cart;
}// Runs inside a transaction.
@Transactional
public Order createOrderAfterPayment(String shippingAddress) {

    // Load the latest cart.
    Cart cart = getMyCart();

    // Create a new order.
    Order order = new Order();

    // Associate the order with the current user.
    order.setUser(cart.getUser());

    // Save the shipping address.
    order.setShippingAddress(shippingAddress.trim());

    // Since payment is already completed, mark the order as PAID.
    order.setStatus(Order.Status.PAID);

    // Variable used to calculate the total amount.
    BigDecimal total = BigDecimal.ZERO;

    // Loop through every cart item.
    for (CartItem ci : cart.getItems()) {

        // Lock the product row before updating stock.
        Product product = productRepository.findByIdForUpdate(ci.getProduct().getId())
                .orElseThrow(() -> new NotFoundException("Product not found"));

        // Reduce available stock.
        product.setStockQuantity(product.getStockQuantity() - ci.getQuantity());

        // Create a new OrderItem.
        OrderItem orderItem = new OrderItem();

        // Associate it with the order.
        orderItem.setOrder(order);

        // Store the purchased product.
        orderItem.setProduct(product);

        // Store quantity.
        orderItem.setQuantity(ci.getQuantity());

        // Calculate line total.
        orderItem.setLineTotal(
                product.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()))
        );

        // Add the item to the order.
        order.getItems().add(orderItem);

        // Add to overall total.
        total = total.add(orderItem.getLineTotal());
    }

    // Save the final order total.
    order.setTotal(total);

    // Save the order.
    Order savedOrder = orderRepository.save(order);

    // Empty the cart.
    cart.getItems().clear();

    // Reset cart total.
    cart.setTotal(BigDecimal.ZERO);

    // Return the saved order.
    return savedOrder;
}
	private static void recalc(Cart cart) { // Recomputes the cart total from its items.
		BigDecimal total = cart.getItems().stream() // Starts a stream over the cart items.
			.map(CartItem::getLineTotal) // Extracts each line total.
			.reduce(BigDecimal.ZERO, BigDecimal::add); // Adds all line totals together.
		cart.setTotal(total); // Writes the computed total back to the cart.
	} // Ends recalc.
} // Ends CartService.

