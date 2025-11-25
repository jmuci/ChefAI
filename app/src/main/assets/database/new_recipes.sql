
-- Sample data for ChefAI database with standard UUIDs

-- Test User
INSERT INTO `users` (`uuid`, `displayName`, `email`, `avatarUrl`, `updatedAt`, `deletedAt`, `syncState`) VALUES
('f47ac10b-58cc-4372-a567-0e02b2c3d479', 'Test User', 'test.user@example.com', 'https://lh3.googleusercontent.com/a/ACg8ocJkiHEp4Du1l-Y4-raRQ6opmwP2Dihq9JIi47wJUn1Aki3d_8Z6=s288-c-no', 1672531200, null, 'SYNCED');

-- Ingredients
INSERT INTO `ingredients` (`uuid`, `displayName`, `allergenId`, `sourcePrimaryId`, `updatedAt`, `deletedAt`, `syncState`) VALUES
('01c7b3e3-802c-4b6e-8e5f-2b8e3a3f5c7c', 'Spaghetti', null, null, 1672531200, null, 'SYNCED'),
('a2b8d4e0-5b27-4d9f-8c3e-9b6e2d1c3a4f', 'Guanciale', null, null, 1672531200, null, 'SYNCED'),
('c3e9a4f2-3b6d-4c8e-9f2a-1c8b4d6e2a1b', 'Eggs', null, null, 1672531200, null, 'SYNCED'),
('d4b3a2c1-8e9f-4b7d-6a5c-4d3b2a1c8e9f', 'Pecorino Romano Cheese', null, null, 1672531200, null, 'SYNCED'),
('e5f2a1b0-9c8d-4e6f-7b4a-3c2d1b8e9f7a', 'Black Pepper', null, null, 1672531200, null, 'SYNCED'),
('f6a1b2c3-d4e5-4f6a-8b9c-0d1e2f3a4b5c', 'Chicken Breast', null, null, 1672531200, null, 'SYNCED'),
('17b2c3d4-e5f6-4a8b-9c0d-1e2f3a4b5c6d', 'Broccoli', null, null, 1672531200, null, 'SYNCED'),
('28c3d4e5-f6a7-4b9c-8d1e-2f3a4b5c6d7e', 'Garlic', null, null, 1672531200, null, 'SYNCED'),
('39d4e5f6-a7b8-4c0d-9e2f-3a4b5c6d7e8f', 'Olive Oil', null, null, 1672531200, null, 'SYNCED'),
('4a5e6f7a-b8c9-4d1e-8f3a-4b5c6d7e8f9a', 'Salmon Fillet', null, null, 1672531200, null, 'SYNCED'),
('5b6f7a8b-c9d0-4e2f-9a4b-5c6d7e8f9a0b', 'Lemon', null, null, 1672531200, null, 'SYNCED'),
('6c7a8b9c-d0e1-4f3a-ab5c-6d7e8f9a0b1c', 'Dill', null, null, 1672531200, null, 'SYNCED'),
('7d8b9c0d-e1f2-4a4b-bc6d-7e8f9a0b1c2d', 'Beef Mince', null, null, 1672531200, null, 'SYNCED'),
('8e9c0d1e-f2a3-4b5c-cd7e-8f9a0b1c2d3e', 'Onion', null, null, 1672531200, null, 'SYNCED'),
('9f0d1e2f-a3b4-4c6d-de8f-9a0b1c2d3e4f', 'Tomatoes', null, null, 1672531200, null, 'SYNCED'),
('a01e2f3a-b4c5-4d7e-ef9a-0b1c2d3e4f5a', 'Kidney Beans', null, null, 1672531200, null, 'SYNCED'),
('b12f3a4b-c5d6-4e8f-f0ab-1c2d3e4f5a6b', 'Chilli Powder', null, null, 1672531200, null, 'SYNCED'),
('c23a4b5c-d6e7-4f9a-01bc-2d3e4f5a6b7c', 'Avocado', null, null, 1672531200, null, 'SYNCED'),
('d34b5c6d-e7f8-4a0b-12cd-3e4f5a6b7c8d', 'Coriander', null, null, 1672531200, null, 'SYNCED'),
('e45c6d7e-f8a9-4b1c-23de-4f5a6b7c8d9e', 'Lime', null, null, 1672531200, null, 'SYNCED');

-- Labels
INSERT INTO `labels` (`uuid`, `displayName`, `updatedAt`, `deletedAt`, `syncState`) VALUES
('f56d7e8f-a9b0-4c2d-34ef-5a6b7c8d9e0f', 'Italian', 1672531200, null, 'SYNCED'),
('a67e8f9a-b0c1-4d3e-45fa-6b7c8d9e0f1a', 'Weeknight Dinner', 1672531200, null, 'SYNCED'),
('b78f9a0b-c1d2-4e4f-56ab-7c8d9e0f1a2b', 'Healthy', 1672531200, null, 'SYNCED'),
('c89a0b1c-d2e3-4f5a-67bc-8d9e0f1a2b3c', 'Seafood', 1672531200, null, 'SYNCED'),
('d90b1c2d-e3f4-4a6b-78cd-9e0f1a2b3c4d', 'Mexican', 1672531200, null, 'SYNCED'),
('e01c2d3e-f4a5-4b7c-89de-0f1a2b3c4d5e', 'Comfort Food', 1672531200, null, 'SYNCED'),
('f12d3e4f-a5b6-4c8d-9aef-1a2b3c4d5e6f', 'Vegetarian', 1672531200, null, 'SYNCED'),
('a23e4f5a-b6c7-4d9e-af0a-2b3c4d5e6f7a', 'Vegan', 1672531200, null, 'SYNCED'),
('b34f5a6b-c7d8-4eaf-b01b-3c4d5e6f7a8b', 'Gluten-Free', 1672531200, null, 'SYNCED'),
('c45a6b7c-d8e9-4fba-c12c-4d5e6f7a8b9c', 'Quick & Easy', 1672531200, null, 'SYNCED');

-- Tags
INSERT INTO `tags` (`uuid`, `displayName`, `updatedAt`, `deletedAt`, `syncState`) VALUES
('d56b7c8d-e9f0-4acb-d23d-5e6f7a8b9c0d', 'pasta', 1672531200, null, 'SYNCED'),
('e67c8d9e-f0a1-4bdb-e34e-6f7a8b9c0d1e', 'chicken', 1672531200, null, 'SYNCED'),
('f78d9e0f-a1b2-4cec-f45f-7a8b9c0d1e2f', 'fish', 1672531200, null, 'SYNCED'),
('a89e0f1a-b2c3-4dfd-a56a-8b9c0d1e2f3a', 'beef', 1672531200, null, 'SYNCED'),
('b90f1a2b-c3d4-4efc-b67b-9c0d1e2f3a4b', 'salad', 1672531200, null, 'SYNCED'),
('c01a2b3c-d4e5-4f0d-c78c-0d1e2f3a4b5c', 'soup', 1672531200, null, 'SYNCED'),
('d12b3c4d-e5f6-4a1e-d89d-1e2f3a4b5c6d', 'spicy', 1672531200, null, 'SYNCED'),
('e23c4d5e-f6a7-4b2f-e9ae-2f3a4b5c6d7e', 'creamy', 1672531200, null, 'SYNCED'),
('f34d5e6f-a7b8-4c3a-fafe-3a4b5c6d7e8f', 'grilled', 1672531200, null, 'SYNCED'),
('a45e6f7a-b8c9-4d4b-ab0f-4b5c6d7e8f9a', 'roasted', 1672531200, null, 'SYNCED');

-- Recipes
INSERT INTO `recipes` (`uuid`, `title`, `description`, `imageUrl`, `imageUrlThumbnail`, `prepTimeMinutes`, `cookTimeMinutes`, `servings`, `creatorId`, `recipeExternalUrl`, `privacy`, `updatedAt`, `deletedAt`, `syncState`) VALUES
('11111111-1111-1111-1111-111111111111', 'Classic Spaghetti Carbonara', 'A traditional Italian pasta dish.', 'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg', 'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg', 10, 20, 4, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('22222222-2222-2222-2222-222222222222', 'Garlic Chicken and Broccoli', 'A simple and healthy one-pan meal.', 'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg', 'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg', 15, 25, 4, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('33333333-3333-3333-3333-333333333333', 'Lemon Dill Salmon', 'Flaky salmon with a fresh, citrusy sauce.', 'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg', 'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg', 10, 15, 2, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('44444444-4444-4444-4444-444444444444', 'Hearty Beef Chilli', 'A warm and comforting bowl of chilli.', 'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg', 'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg', 20, 60, 6, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('55555555-5555-5555-5555-555555555555', 'Avocado Toast with Egg', 'A simple and nutritious breakfast.', 'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg', 'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg', 5, 5, 1, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('66666666-6666-6666-6666-666666666666', 'Pesto Pasta Salad', 'A refreshing pasta salad.', 'https://www.pressurecookrecipes.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg', 'https://www.pressurecookrecipes.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg', 15, 10, 4, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('77777777-7777-7777-7777-777777777777', 'Mushroom Risotto', 'Creamy and savory risotto.', 'https://healthiersteps.com/wp-content/uploads/2019/01/gluten-free-vegan-spinach-soup.jpg', 'https://healthiersteps.com/wp-content/uploads/2019/01/gluten-free-vegan-spinach-soup.jpg', 10, 40, 4, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('88888888-8888-8888-8888-888888888888', 'Chicken Noodle Soup', 'Classic comfort soup.', 'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg', 'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg', 15, 45, 6, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('99999999-9999-9999-9999-999999999999', 'Greek Salad', 'A fresh and tangy salad.', 'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg', 'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg', 15, 0, 4, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Beef Tacos', 'Classic ground beef tacos.', 'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg', 'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg', 20, 15, 4, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Lentil Soup', 'A hearty and healthy soup.', 'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg', 'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg', 10, 50, 6, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('cccccccc-cccc-cccc-cccc-cccccccccccc', 'Sheet Pan Chicken Fajitas', 'An easy and flavorful weeknight meal.', 'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg', 'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg', 15, 25, 4, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('dddddddd-dddd-dddd-dddd-dddddddddddd', 'Caprese Salad', 'Simple and fresh tomato, mozzarella, and basil.', 'https://www.pressurecookrecipes.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg', 'https://www.pressurecookrecipes.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg', 10, 0, 2, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'Garlic Shrimp Scampi', 'A quick and elegant pasta dish.', 'https://healthiersteps.com/wp-content/uploads/2019/01/gluten-free-vegan-spinach-soup.jpg', 'https://healthiersteps.com/wp-content/uploads/2019/01/gluten-free-vegan-spinach-soup.jpg', 10, 15, 2, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('ffffffff-ffff-ffff-ffff-ffffffffffff', 'Butternut Squash Soup', 'A creamy and comforting fall soup.', 'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg', 'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg', 15, 40, 6, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('10101010-1010-1010-1010-101010101010', 'Black Bean Burgers', 'A delicious and satisfying veggie burger.', 'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg', 'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg', 20, 15, 4, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('12121212-1212-1212-1212-121212121212', 'Chicken Alfredo', 'A rich and creamy pasta classic.', 'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg', 'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg', 10, 25, 4, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('13131313-1313-1313-1313-131313131313', 'Roasted Root Vegetables', 'A simple and healthy side dish.', 'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg', 'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg', 15, 45, 4, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('14141414-1414-1414-1414-141414141414', 'Tomato Bruschetta', 'A classic Italian appetizer.', 'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg', 'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg', 15, 5, 4, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
('15151515-1515-1515-1515-151515151515', 'Pad Thai', 'A classic Thai noodle dish.', 'https://www.pressurecookrecipes.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg', 'https://www.pressurecookrecipes.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg', 20, 15, 4, 'f47ac10b-58cc-4372-a567-0e02b2c3d479', null, 'PUBLIC', 1672531200, null, 'SYNCED');


-- Recipe Steps
INSERT INTO `recipe_steps` (`uuid`, `recipeId`, `orderIndex`, `instruction`, `updatedAt`, `deletedAt`, `syncState`) VALUES
('da8a7a8a-5b8e-4b6e-8e5f-2b8e3a3f5c7c', '11111111-1111-1111-1111-111111111111', 1, 'Cook spaghetti according to package directions.', 1672531200, null, 'SYNCED'),
('e4b8d4e0-5b27-4d9f-8c3e-9b6e2d1c3a4f', '11111111-1111-1111-1111-111111111111', 2, 'While pasta is cooking, fry guanciale until crisp.', 1672531200, null, 'SYNCED'),
('f3e9a4f2-3b6d-4c8e-9f2a-1c8b4d6e2a1b', '11111111-1111-1111-1111-111111111111', 3, 'In a bowl, whisk eggs, pecorino romano, and a generous amount of black pepper.', 1672531200, null, 'SYNCED'),
('04b3a2c1-8e9f-4b7d-6a5c-4d3b2a1c8e9f', '11111111-1111-1111-1111-111111111111', 4, 'Drain pasta, reserving some pasta water. Quickly mix hot pasta with the egg mixture and guanciale.', 1672531200, null, 'SYNCED'),
('15f2a1b0-9c8d-4e6f-7b4a-3c2d1b8e9f7a', '22222222-2222-2222-2222-222222222222', 1, 'Preheat oven to 400°F (200°C).', 1672531200, null, 'SYNCED'),
('26a1b2c3-d4e5-4f6a-8b9c-0d1e2f3a4b5c', '22222222-2222-2222-2222-222222222222', 2, 'Toss chicken and broccoli with olive oil, garlic, salt, and pepper.', 1672531200, null, 'SYNCED'),
('37b2c3d4-e5f6-4a8b-9c0d-1e2f3a4b5c6d', '22222222-2222-2222-2222-222222222222', 3, 'Arrange in a single layer on a baking sheet.', 1672531200, null, 'SYNCED'),
('48c3d4e5-f6a7-4b9c-8d1e-2f3a4b5c6d7e', '22222222-2222-2222-2222-222222222222', 4, 'Roast for 20-25 minutes, or until chicken is cooked through.', 1672531200, null, 'SYNCED');

-- Recipe Ingredients (Cross Ref)
INSERT INTO `recipe_ingredients` (`recipeId`, `ingredientId`, `quantity`, `unit`, `updatedAt`, `deletedAt`, `syncState`) VALUES
('11111111-1111-1111-1111-111111111111', '01c7b3e3-802c-4b6e-8e5f-2b8e3a3f5c7c', 400, 'g', 1672531200, null, 'SYNCED'),
('11111111-1111-1111-1111-111111111111', 'a2b8d4e0-5b27-4d9f-8c3e-9b6e2d1c3a4f', 150, 'g', 1672531200, null, 'SYNCED'),
('11111111-1111-1111-1111-111111111111', 'c3e9a4f2-3b6d-4c8e-9f2a-1c8b4d6e2a1b', 4, 'large', 1672531200, null, 'SYNCED'),
('11111111-1111-1111-1111-111111111111', 'd4b3a2c1-8e9f-4b7d-6a5c-4d3b2a1c8e9f', 50, 'g', 1672531200, null, 'SYNCED'),
('11111111-1111-1111-1111-111111111111', 'e5f2a1b0-9c8d-4e6f-7b4a-3c2d1b8e9f7a', 1, 'tsp', 1672531200, null, 'SYNCED'),
('22222222-2222-2222-2222-222222222222', 'f6a1b2c3-d4e5-4f6a-8b9c-0d1e2f3a4b5c', 2, 'breasts', 1672531200, null, 'SYNCED'),
('22222222-2222-2222-2222-222222222222', '17b2c3d4-e5f6-4a8b-9c0d-1e2f3a4b5c6d', 1, 'head', 1672531200, null, 'SYNCED'),
('22222222-2222-2222-2222-222222222222', '28c3d4e5-f6a7-4b9c-8d1e-2f3a4b5c6d7e', 3, 'cloves', 1672531200, null, 'SYNCED'),
('22222222-2222-2222-2222-222222222222', '39d4e5f6-a7b8-4c0d-9e2f-3a4b5c6d7e8f', 2, 'tbsp', 1672531200, null, 'SYNCED'),
('33333333-3333-3333-3333-333333333333', '4a5e6f7a-b8c9-4d1e-8f3a-4b5c6d7e8f9a', 2, 'fillets', 1672531200, null, 'SYNCED'),
('33333333-3333-3333-3333-333333333333', '5b6f7a8b-c9d0-4e2f-9a4b-5c6d7e8f9a0b', 1, 'tbsp', 1672531200, null, 'SYNCED'),
('33333333-3333-3333-3333-333333333333', '6c7a8b9c-d0e1-4f3a-ab5c-6d7e8f9a0b1c', 2, 'tbsp',1672531200, null, 'SYNCED'),
('44444444-4444-4444-4444-444444444444', '7d8b9c0d-e1f2-4a4b-bc6d-7e8f9a0b1c2d', 500, 'g', 1672531200, null, 'SYNCED'),
('44444444-4444-4444-4444-444444444444', '8e9c0d1e-f2a3-4b5c-cd7e-8f9a0b1c2d3e', 1, 'large', 1672531200, null, 'SYNCED'),
('44444444-4444-4444-4444-444444444444', '9f0d1e2f-a3b4-4c6d-de8f-9a0b1c2d3e4f', 400, 'g', 1672531200, null, 'SYNCED'),
('44444444-4444-4444-4444-444444444444', 'a01e2f3a-b4c5-4d7e-ef9a-0b1c2d3e4f5a', 400, 'g', 1672531200, null, 'SYNCED'),
('44444444-4444-4444-4444-444444444444', 'b12f3a4b-c5d6-4e8f-f0ab-1c2d3e4f5a6b', 2, 'tbsp', 1672531200, null, 'SYNCED'),
('55555555-5555-5555-5555-555555555555', 'c23a4b5c-d6e7-4f9a-01bc-2d3e4f5a6b7c', 1, 'large', 1672531200, null, 'SYNCED'),
('55555555-5555-5555-5555-555555555555', 'c3e9a4f2-3b6d-4c8e-9f2a-1c8b4d6e2a1b', 1, 'large', 1672531200, null, 'SYNCED'),
('55555555-5555-5555-5555-555555555555', 'd34b5c6d-e7f8-4a0b-12cd-3e4f5a6b7c8d', 1, 'tbsp', 1672531200, null, 'SYNCED'),
('55555555-5555-5555-5555-555555555555', 'e45c6d7e-f8a9-4b1c-23de-4f5a6b7c8d9e', 0.5, 'cup', 1672531200, null, 'SYNCED');

-- Recipe Labels (Cross Ref)
INSERT INTO `recipe_labels` (`recipeId`, `labelId`, `updatedAt`, `deletedAt`, `syncState`) VALUES
('11111111-1111-1111-1111-111111111111', 'f56d7e8f-a9b0-4c2d-34ef-5a6b7c8d9e0f', 1672531200, null, 'SYNCED'),
('11111111-1111-1111-1111-111111111111', 'a67e8f9a-b0c1-4d3e-45fa-6b7c8d9e0f1a', 1672531200, null, 'SYNCED'),
('22222222-2222-2222-2222-222222222222', 'a67e8f9a-b0c1-4d3e-45fa-6b7c8d9e0f1a', 1672531200, null, 'SYNCED'),
('22222222-2222-2222-2222-222222222222', 'b78f9a0b-c1d2-4e4f-56ab-7c8d9e0f1a2b', 1672531200, null, 'SYNCED'),
('33333333-3333-3333-3333-333333333333', 'c89a0b1c-d2e3-4f5a-67bc-8d9e0f1a2b3c', 1672531200, null, 'SYNCED'),
('33333333-3333-3333-3333-333333333333', 'c45a6b7c-d8e9-4fba-c12c-4d5e6f7a8b9c', 1672531200, null, 'SYNCED'),
('44444444-4444-4444-4444-444444444444', 'd90b1c2d-e3f4-4a6b-78cd-9e0f1a2b3c4d', 1672531200, null, 'SYNCED'),
('44444444-4444-4444-4444-444444444444', 'e01c2d3e-f4a5-4b7c-89de-0f1a2b3c4d5e', 1672531200, null, 'SYNCED'),
('55555555-5555-5555-5555-555555555555', 'b78f9a0b-c1d2-4e4f-56ab-7c8d9e0f1a2b', 1672531200, null, 'SYNCED'),
('55555555-5555-5555-5555-555555555555', 'c45a6b7c-d8e9-4fba-c12c-4d5e6f7a8b9c', 1672531200, null, 'SYNCED');

-- Recipe Tags (Cross Ref)
INSERT INTO `recipe_tags` (`recipeId`, `tagId`, `updatedAt`, `deletedAt`, `syncState`) VALUES
('11111111-1111-1111-1111-111111111111', 'd56b7c8d-e9f0-4acb-d23d-5e6f7a8b9c0d', 1672531200, null, 'SYNCED'),
('11111111-1111-1111-1111-111111111111', 'e23c4d5e-f6a7-4b2f-e9ae-2f3a4b5c6d7e', 1672531200, null, 'SYNCED'),
('22222222-2222-2222-2222-222222222222', 'e67c8d9e-f0a1-4bdb-e34e-6f7a8b9c0d1e', 1672531200, null, 'SYNCED'),
('22222222-2222-2222-2222-222222222222', 'a45e6f7a-b8c9-4d4b-ab0f-4b5c6d7e8f9a', 1672531200, null, 'SYNCED'),
('33333333-3333-3333-3333-333333333333', 'f78d9e0f-a1b2-4cec-f45f-7a8b9c0d1e2f', 1672531200, null, 'SYNCED'),
('33333333-3333-3333-3333-333333333333', 'f34d5e6f-a7b8-4c3a-fafe-3a4b5c6d7e8f', 1672531200, null, 'SYNCED'),
('44444444-4444-4444-4444-444444444444', 'a89e0f1a-b2c3-4dfd-a56a-8b9c0d1e2f3a', 1672531200, null, 'SYNCED'),
('44444444-4444-4444-4444-444444444444', 'd12b3c4d-e5f6-4a1e-d89d-1e2f3a4b5c6d', 1672531200, null, 'SYNCED'),
('55555555-5555-5555-5555-555555555555', 'b90f1a2b-c3d4-4efc-b67b-9c0d1e2f3a4b', 1672531200, null, 'SYNCED');
